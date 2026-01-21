package com.aerofin.controller;

import com.aerofin.agent.MultiAgentOrchestrator;
import com.aerofin.model.dto.ChatRequest;
import com.aerofin.service.AeroFinAgentService;
import com.aerofin.service.ConversationService;
import com.aerofin.service.ResumeConversationService;
import com.aerofin.service.ResumeConversationService.ResumeResult;
import com.aerofin.service.ResumeConversationService.SessionSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

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
@Tag(name = "Chat API", description = "智能客服聊天接口 - 支持流式/非流式对话、多Agent协作、断点续聊")
public class ChatController {

    private final AeroFinAgentService agentService;
    private final MultiAgentOrchestrator multiAgentOrchestrator;
    private final ConversationService conversationService;
    private final ResumeConversationService resumeConversationService;

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
    @Operation(summary = "流式对话接口（SSE）", description = "支持Server-Sent Events实时打字机效果，适用于需要逐字输出的场景")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功", content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "401", description = "未授权 - API Key无效"),
            @ApiResponse(responseCode = "429", description = "请求过于频繁 - 超过限流阈值")
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @Parameter(description = "用户消息内容", required = true, example = "我想贷款20万，3年还清，每月还多少？")
            @RequestParam String message,
            @Parameter(description = "会话ID（可选，用于多轮对话）", example = "session-abc123")
            @RequestParam(required = false) String sessionId,
            @Parameter(description = "用户ID（可选）", example = "user-123")
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
     * 多Agent协作流式对话接口（SSE）
     * <p>
     * 接口路径：GET /api/chat/multi-agent/stream
     * 响应类型：text/event-stream（SSE）
     * <p>
     * 特点：
     * 1. 自动判断是否需要多Agent协作
     * 2. 根据用户消息内容智能路由到单个或多个Agent
     * 3. 多Agent场景下并行执行并聚合结果
     * <p>
     * 面试要点：
     * - 多Agent协作编排
     * - 智能意图识别
     * - 结果聚合策略
     *
     * @param message   用户消息
     * @param sessionId 会话ID（可选）
     * @param userId    用户ID（可选）
     */
    @GetMapping(value = "/multi-agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> multiAgentChatStream(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId) {

        log.info("Received multi-agent stream request: message={}, sessionId={}, userId={}",
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

        // 3. 调用 MultiAgentOrchestrator，自动判断单/多Agent
        Flux<String> contentStream = multiAgentOrchestrator.processRequestStream(
                message, finalSessionId, finalUserId);

        // 4. 转换为 SSE 格式
        return contentStream
                .map(chunk -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("message")
                        .data(chunk)
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("[DONE]")
                                .build()
                ))
                .mergeWith(Flux.interval(Duration.ofSeconds(30))
                        .map(tick -> ServerSentEvent.<String>builder()
                                .event("heartbeat")
                                .data("ping")
                                .build())
                )
                .doOnComplete(() -> log.info("Multi-agent stream completed for session: {}", finalSessionId))
                .doOnError(error -> log.error("Multi-agent stream error for session: {}", finalSessionId, error));
    }

    /**
     * 多Agent协作对话接口（非流式）
     * <p>
     * 接口路径：POST /api/chat/multi-agent
     * 响应类型：application/json
     * <p>
     * 特点：
     * - 自动判断是否需要多Agent协作
     * - 返回聚合后的完整结果
     */
    @PostMapping("/multi-agent")
    public String multiAgentChat(@Valid @RequestBody ChatRequest request) {
        log.info("Received multi-agent request: {}", request);

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = conversationService.createSession(request.getUserId());
        }

        String userId = (request.getUserId() != null && !request.getUserId().isBlank())
                ? request.getUserId() : "anonymous";

        // 使用 MultiAgentOrchestrator 处理请求
        return multiAgentOrchestrator.processRequest(request.getMessage(), sessionId, userId)
                .block();
    }

    /**
     * 多Agent + ReflectAgent 二次审阅（非流式）
     * <p>
     * 接口路径：POST /api/chat/multi-agent/reflect
     * <p>
     * 用途：
     * - 先由编排器完成正常路由与专家回答（draft）
     * - 再由 ReflectAgent 对回答做合规/风险/逻辑二次审阅并输出修订版
     */
    @PostMapping("/multi-agent/reflect")
    public Mono<String> multiAgentChatWithReflection(@Valid @RequestBody ChatRequest request) {
        log.info("Received multi-agent reflect request: {}", request);

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = conversationService.createSession(request.getUserId());
        }

        String userId = (request.getUserId() != null && !request.getUserId().isBlank())
                ? request.getUserId() : "anonymous";

        return multiAgentOrchestrator.processRequestWithReflection(request.getMessage(), sessionId, userId);
    }

    /**
     * 暂停会话（保存快照）
     * <p>
     * 接口路径：POST /api/chat/session/{sessionId}/pause
     * 用途：用户离开时保存会话快照，支持之后恢复
     * <p>
     * 返回：快照ID（用于恢复时使用）
     * <p>
     * 示例：
     * ```bash
     * curl -X POST "http://localhost:8080/api/chat/session/session-123/pause?userId=user-456"
     * ```
     * 返回：
     * ```json
     * {
     *   "snapshotId": "snapshot:session-123",
     *   "success": true,
     *   "message": "会话已暂停"
     * }
     * ```
     */
    @Operation(summary = "暂停会话", description = "保存当前会话快照，用户可以之后恢复")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功暂停会话"),
            @ApiResponse(responseCode = "500", description = "服务器错误")
    })
    @PostMapping("/session/{sessionId}/pause")
    public Mono<ResponseEntity<PauseSessionResponse>> pauseSession(
            @Parameter(description = "会话ID", required = true)
            @PathVariable String sessionId,
            @Parameter(description = "用户ID（可选）")
            @RequestParam(required = false) String userId) {

        log.info("⏸️ Pausing session: sessionId={}, userId={}", sessionId, userId);

        return Mono.fromCallable(() -> {
            try {
                String snapshotId = resumeConversationService.pauseSession(sessionId, userId);
                return ResponseEntity.ok(PauseSessionResponse.builder()
                        .success(true)
                        .snapshotId(snapshotId)
                        .message("会话已暂停，快照ID: " + snapshotId)
                        .build());
            } catch (Exception e) {
                log.error("Failed to pause session: sessionId={}", sessionId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(PauseSessionResponse.builder()
                                .success(false)
                                .message("暂停会话失败: " + e.getMessage())
                                .build());
            }
        });
    }

    /**
     * 恢复会话（加载快照）
     * <p>
     * 接口路径：POST /api/chat/session/resume
     * 用途：通过快照ID恢复之前暂停的会话
     * <p>
     * 示例：
     * ```bash
     * curl -X POST "http://localhost:8080/api/chat/session/resume?snapshotId=snapshot:session-123"
     * ```
     * 返回：
     * ```json
     * {
     *   "success": true,
     *   "sessionId": "session-123",
     *   "userId": "user-456",
     *   "summary": "欢迎回来！\n上次对话时间：2024-01-20 15:30\n请继续您的问题..."
     * }
     * ```
     */
    @PostMapping("/session/resume")
    public Mono<ResponseEntity<ResumeResult>> resumeSession(
            @RequestParam String snapshotId) {

        log.info("▶️ Resuming session: snapshotId={}", snapshotId);

        return Mono.fromCallable(() -> {
            try {
                ResumeResult result = resumeConversationService.resumeSession(snapshotId);
                if (result.getSuccess()) {
                    return ResponseEntity.ok(result);
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
                }
            } catch (Exception e) {
                log.error("Failed to resume session: snapshotId={}", snapshotId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ResumeResult.failure("恢复会话失败: " + e.getMessage()));
            }
        });
    }

    /**
     * 获取用户的可恢复会话列表
     * <p>
     * 接口路径：GET /api/chat/sessions/recoverable
     * 用途：展示用户所有可恢复的历史会话
     * <p>
     * 示例：
     * ```bash
     * curl "http://localhost:8080/api/chat/sessions/recoverable?userId=user-456"
     * ```
     * 返回：
     * ```json
     * [
     *   {
     *     "sessionId": "session-123",
     *     "title": "会话 session-123",
     *     "lastMessageTime": "2024-01-20T15:30:00",
     *     "messageCount": 10,
     *     "preview": "上次讨论的主题..."
     *   }
     * ]
     * ```
     */
    @GetMapping("/sessions/recoverable")
    public Mono<ResponseEntity<List<SessionSummary>>> getRecoverableSessions(
            @RequestParam String userId) {

        log.info("📋 Fetching recoverable sessions for user: {}", userId);

        return Mono.fromCallable(() -> {
            try {
                List<SessionSummary> sessions = resumeConversationService.getRecoverableSessions(userId);
                log.info("Found {} recoverable sessions for user: {}", sessions.size(), userId);
                return ResponseEntity.ok(sessions);
            } catch (Exception e) {
                log.error("Failed to fetch recoverable sessions for user: {}", userId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        });
    }

    /**
     * 检查会话是否可恢复
     * <p>
     * 接口路径：GET /api/chat/session/{sessionId}/can-resume
     * 用途：检查特定会话是否存在快照可用于恢复
     * <p>
     * 示例：
     * ```bash
     * curl "http://localhost:8080/api/chat/session/session-123/can-resume"
     * ```
     * 返回：
     * ```json
     * {
     *   "canResume": true,
     *   "message": "会话可恢复"
     * }
     * ```
     */
    @GetMapping("/session/{sessionId}/can-resume")
    public Mono<ResponseEntity<CanResumeResponse>> canResumeSession(
            @PathVariable String sessionId) {

        log.info("🔍 Checking if session can be resumed: {}", sessionId);

        return Mono.fromCallable(() -> {
            try {
                boolean canResume = resumeConversationService.canResumeSession(sessionId);
                return ResponseEntity.ok(CanResumeResponse.builder()
                        .canResume(canResume)
                        .message(canResume ? "会话可恢复" : "会话不存在或已过期")
                        .build());
            } catch (Exception e) {
                log.error("Failed to check if session can be resumed: {}", sessionId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        });
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

    // ==================== 响应 DTO ====================

    /**
     * 暂停会话响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PauseSessionResponse {
        private Boolean success;
        private String snapshotId;
        private String message;
    }

    /**
     * 检查会话可恢复性响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CanResumeResponse {
        private Boolean canResume;
        private String message;
    }
}
