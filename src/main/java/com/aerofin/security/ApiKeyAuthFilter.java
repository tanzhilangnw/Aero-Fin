package com.aerofin.security;

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
import java.util.Arrays;
import java.util.List;

/**
 * API Key 认证过滤器
 * <p>
 * 核心功能：
 * 1. 验证请求头中的 X-API-Key
 * 2. 白名单路径无需认证（/health, /actuator）
 * 3. 返回401未授权错误（如果API Key无效）
 * <p>
 * 面试亮点：
 * - WebFlux响应式过滤器
 * - 安全认证实现
 * - 白名单机制
 * - 统一错误响应
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
@Order(1) // 最高优先级，第一个执行
@RequiredArgsConstructor
public class ApiKeyAuthFilter implements WebFilter {

    private final SecurityProperties securityProperties;

    /**
     * 白名单路径（无需认证）
     */
    private static final List<String> WHITELIST_PATHS = Arrays.asList(
            "/api/chat/health",
            "/actuator/health",
            "/actuator/prometheus",
            "/actuator/metrics"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();

        // 1. 白名单路径直接放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 2. 检查是否启用了认证
        if (!securityProperties.getEnabled()) {
            log.debug("API认证已禁用，直接放行: path={}", path);
            return chain.filter(exchange);
        }

        // 3. 提取API Key
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");

        // 4. 验证API Key
        if (isValidApiKey(apiKey)) {
            log.debug("API认证成功: apiKey={}, path={}", maskApiKey(apiKey), path);
            // 将userId存储到请求属性中（用于限流）
            exchange.getAttributes().put("userId", extractUserIdFromApiKey(apiKey));
            return chain.filter(exchange);
        } else {
            log.warn("API认证失败: apiKey={}, path={}", maskApiKey(apiKey), path);
            return unauthorized(exchange);
        }
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        return WHITELIST_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 验证API Key是否有效
     */
    private boolean isValidApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return securityProperties.getApiKeys().contains(apiKey);
    }

    /**
     * 从API Key提取用户ID（用于限流）
     * 格式：sk-aerofin-prod-{userId}-{random}
     */
    private String extractUserIdFromApiKey(String apiKey) {
        try {
            // 简化版：使用API Key作为userId
            // 生产环境可以从API Key中解析出真实的userId
            return apiKey.substring(0, Math.min(10, apiKey.length()));
        } catch (Exception e) {
            return "anonymous";
        }
    }

    /**
     * 脱敏API Key（日志用）
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return "invalid";
        }
        return apiKey.substring(0, 10) + "***";
    }

    /**
     * 返回401未授权错误
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String errorJson = """
                {
                  "error": "Unauthorized",
                  "message": "无效的API Key，请在请求头中提供有效的 X-API-Key",
                  "status": 401,
                  "path": "%s"
                }
                """.formatted(exchange.getRequest().getPath());

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(errorJson.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
