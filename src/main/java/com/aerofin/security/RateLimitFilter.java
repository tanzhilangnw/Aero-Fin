package com.aerofin.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 限流过滤器
 * <p>
 * 核心功能：
 * 1. 使用Bucket4j令牌桶算法限流
 * 2. 按用户ID限流（从API Key或JWT中提取）
 * 3. 添加限流相关响应头
 * 4. 返回429状态码（请求过于频繁）
 * <p>
 * 配置示例：
 * ```yaml
 * aero-fin:
 *   rate-limit:
 *     enabled: true
 *     requests-per-minute: 60
 *     burst-capacity: 10
 * ```
 * <p>
 * 面试亮点：
 * - 令牌桶算法
 * - 并发限流
 * - 响应头规范
 * - 脱敏日志
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
@Order(2) // 在ApiKeyAuthFilter之后执行
@RequiredArgsConstructor
public class RateLimitFilter implements WebFilter {

    private final RateLimitProperties rateLimitProperties;

    /**
     * 每个用户的令牌桶缓存
     * key: userId, value: Bucket
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 白名单路径（不限流）
     */
    private static final List<String> WHITELIST_PATHS = Arrays.asList(
            "/api/chat/health",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();

        // 1. 白名单路径和禁用状态直接放行
        if (isWhitelisted(path) || !rateLimitProperties.getEnabled()) {
            return chain.filter(exchange);
        }

        // 2. 获取用户ID（从请求属性或请求头）
        String userId = getUserId(exchange);

        // 3. 获取或创建用户的令牌桶
        Bucket bucket = buckets.computeIfAbsent(userId, k -> createBucket());

        // 4. 尝试消费令牌
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // 消费成功，添加限流响应头
            exchange.getResponse().getHeaders().add("X-RateLimit-Limit",
                    String.valueOf(rateLimitProperties.getRequestsPerMinute()));
            exchange.getResponse().getHeaders().add("X-RateLimit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            exchange.getResponse().getHeaders().add("X-RateLimit-Reset",
                    String.valueOf(System.currentTimeMillis() / 1000 + 60));

            log.debug("限流检查通过: userId={}, remaining={}", userId, probe.getRemainingTokens());
            return chain.filter(exchange);
        } else {
            // 消费失败，返回429
            log.warn("限流触发: userId={}, path={}", userId, path);
            return tooManyRequests(exchange, probe);
        }
    }

    /**
     * 创建令牌桶
     * 配置：每分钟requestsPerMinute个请求 + 突发容量burstCapacity
     */
    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rateLimitProperties.getRequestsPerMinute() + rateLimitProperties.getBurstCapacity())
                .refillIntervally(rateLimitProperties.getRequestsPerMinute(), Duration.ofMinutes(1))
                .build();

        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * 获取用户ID
     * 优先级：请求属性 > X-User-Id请求头 > IP地址
     */
    private String getUserId(ServerWebExchange exchange) {
        // 优先级1：从ApiKeyAuthFilter设置的属性获取
        Object userIdAttr = exchange.getAttribute("userId");
        if (userIdAttr != null) {
            return userIdAttr.toString();
        }

        // 优先级2：从请求头获取
        String userIdHeader = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            return userIdHeader;
        }

        // 优先级3：使用IP地址
        String remoteAddress = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return remoteAddress != null ? remoteAddress : "anonymous";
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        return WHITELIST_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 返回429 Too Many Requests
     */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange, ConsumptionProbe probe) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 计算需要等待的秒数
        long waitForRefill = probe.getRoundedSecondsToWait();

        // 添加Retry-After响应头
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(waitForRefill));

        String errorJson = """
                {
                  "error": "Too Many Requests",
                  "message": "请求过于频繁，请稍后再试",
                  "status": 429,
                  "retry_after": %d,
                  "path": "%s"
                }
                """.formatted(waitForRefill, exchange.getRequest().getPath());

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(errorJson.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
