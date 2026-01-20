package com.aerofin.controller;

import com.aerofin.model.dto.ChatRequest;
import com.aerofin.service.AeroFinAgentService;
import com.aerofin.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 聊天接口 Controller
 * <p>
 * 核心功能：
 * 1. SSE 流式对话（打字机效果）
 * 2. 会话管理（创建、查询）
 * 3. 参数校验
 * <p>
 * 面试亮点：
 * - Spring WebFlux 响应式编程
 * - Server-Sent Events (SSE) 实现
 * - 流式响应的错误处理
 * - 心跳保活机制
 *
 * @author Aero-Fin Team
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 生产环境请配置具体域名
public class ChatController {

    private final AeroFinAgentService agentService;
    private final ConversationService conversationService;

    /**
     * 流式对话接口（SSE）
     * <p>
     * 接口路径：GET /api/chat/stream
     * 响应类型：text/event-stream（SSE）
     * <p>
     * 面试要点：
     * 1. 使用 Flux<ServerSentEvent> 实现 SSE
     * 2. 每个 chunk 作为一个 event 发送
     * 3. 前端接收到的是实时流式数据（打字机效果）
     * 4. 添加心跳机制防止连接超时
     * <p>
     * 前端示例代码：
     * ```javascript
     * const eventSource = new EventSource('/api/chat/stream?message=你好');
     * eventSource.onmessage = (event) => {
     *   console.log(event.data); // 实时接收每个 chunk
     * };
     * ```
     *
     * @param message 用户消息
     * @param sessionId 会话ID（可选）
     * @param userId 用户ID（可选）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId) {

        log.info("Received stream request: message={}, sessionId={}, userId={}",
                truncate(message, 100), sessionId, userId);

        // 1. 如果没有 sessionId，创建新会话
        String actualSessionId = sessionId;
        if (actualSessionId == null || actualSessionId.isBlank()) {
            actualSessionId = conversationService.createSession(userId);
            log.info("Created new session: {}", actualSessionId);
        }

        // 2. 如果没有 userId，使用默认值
        String actualUserId = (userId != null && !userId.isBlank()) ? userId : "anonymous";

        final String finalSessionId = actualSessionId;
        final String finalUserId = actualUserId;

        // 3. 调用 Agent 服务，获取流式响应
        Flux<String> contentStream = agentService.chatStream(finalSessionId, finalUserId, message);

        // 4. 转换为 SSE 格式
        return contentStream
                .map(chunk -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("message")
                        .data(chunk)
                        .build())
                // 流结束时发送完成事件
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("[DONE]")
                                .build()
                ))
                // 添加心跳机制（每 30 秒发送一个心跳）
                .mergeWith(Flux.interval(Duration.ofSeconds(30))
                        .map(tick -> ServerSentEvent.<String>builder()
                                .event("heartbeat")
                                .data("ping")
                                .build())
                )
                .doOnComplete(() -> log.info("Stream completed for session: {}", finalSessionId))
                .doOnError(error -> log.error("Stream error for session: {}", finalSessionId, error));
    }

    /**
     * POST 方式流式对话（支持复杂参数）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamPost(@Valid @RequestBody ChatRequest request) {
        log.info("Received POST stream request: {}", request);

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = conversationService.createSession(request.getUserId());
        }

        String userId = (request.getUserId() != null && !request.getUserId().isBlank())
                ? request.getUserId() : "anonymous";

        final String finalSessionId = sessionId;
        final String finalUserId = userId;

        Flux<String> contentStream = agentService.chatStream(finalSessionId, finalUserId, request.getMessage());

        return contentStream
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data(String.format("{\"sessionId\": \"%s\"}", finalSessionId))
                                .build()
                ));
    }

    /**
     * 非流式对话（用于测试）
     * <p>
     * 接口路径：POST /api/chat
     * 响应类型：application/json
     */
    @PostMapping
    public String chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received non-stream request: {}", request);

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = conversationService.createSession(request.getUserId());
        }

        String userId = (request.getUserId() != null && !request.getUserId().isBlank())
                ? request.getUserId() : "anonymous";

        return agentService.chat(sessionId, userId, request.getMessage());
    }

    /**
     * 创建新会话
     */
    @PostMapping("/session")
    public String createSession(@RequestParam(required = false) String userId) {
        String sessionId = conversationService.createSession(userId);
        log.info("Created session: {} for user: {}", sessionId, userId);
        return sessionId;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    /**
     * 截断字符串（日志用）
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
