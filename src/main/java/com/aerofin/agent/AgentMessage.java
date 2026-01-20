package com.aerofin.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent间通信消息
 * <p>
 * 用于多Agent协作中的消息传递：
 * 1. 任务分发消息（Coordinator → Expert）
 * 2. 结果返回消息（Expert → Coordinator）
 * 3. 协作请求消息（Expert → Expert）
 * <p>
 * 面试亮点：
 * - Agent间异步通信机制
 * - 消息路由与追踪
 * - 支持流式传输
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {

    /**
     * 消息ID（用于追踪）
     */
    private String messageId;

    /**
     * 发送者角色
     */
    private AgentRole sender;

    /**
     * 接收者角色
     */
    private AgentRole receiver;

    /**
     * 消息类型
     */
    private MessageType messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 结构化数据（用于工具调用参数、结果等）
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /**
     * 消息优先级（0-10，数字越大优先级越高）
     */
    @Builder.Default
    private Integer priority = 5;

    /**
     * 是否需要响应
     */
    @Builder.Default
    private Boolean requiresResponse = true;

    /**
     * 父消息ID（用于追踪对话链）
     */
    private String parentMessageId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 创建时间
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /**
         * 任务分发（Coordinator → Expert）
         */
        TASK_ASSIGNMENT,

        /**
         * 结果返回（Expert → Coordinator）
         */
        TASK_RESULT,

        /**
         * 协作请求（Expert → Expert）
         */
        COLLABORATION_REQUEST,

        /**
         * 信息查询（Agent → Agent）
         */
        INFORMATION_QUERY,

        /**
         * 确认/通知（Agent → Agent）
         */
        CONFIRMATION,

        /**
         * 错误报告（Agent → Coordinator）
         */
        ERROR_REPORT
    }

    /**
     * 创建任务分发消息
     */
    public static AgentMessage createTaskAssignment(
            AgentRole sender,
            AgentRole receiver,
            String content,
            String sessionId) {
        return AgentMessage.builder()
                .messageId(generateMessageId())
                .sender(sender)
                .receiver(receiver)
                .messageType(MessageType.TASK_ASSIGNMENT)
                .content(content)
                .sessionId(sessionId)
                .requiresResponse(true)
                .build();
    }

    /**
     * 创建结果返回消息
     */
    public static AgentMessage createTaskResult(
            AgentRole sender,
            AgentRole receiver,
            String content,
            String parentMessageId,
            String sessionId) {
        return AgentMessage.builder()
                .messageId(generateMessageId())
                .sender(sender)
                .receiver(receiver)
                .messageType(MessageType.TASK_RESULT)
                .content(content)
                .parentMessageId(parentMessageId)
                .sessionId(sessionId)
                .requiresResponse(false)
                .build();
    }

    /**
     * 创建协作请求消息
     */
    public static AgentMessage createCollaborationRequest(
            AgentRole sender,
            AgentRole receiver,
            String content,
            String sessionId) {
        return AgentMessage.builder()
                .messageId(generateMessageId())
                .sender(sender)
                .receiver(receiver)
                .messageType(MessageType.COLLABORATION_REQUEST)
                .content(content)
                .sessionId(sessionId)
                .requiresResponse(true)
                .priority(7) // 协作请求优先级较高
                .build();
    }

    /**
     * 添加结构化数据
     */
    public AgentMessage addData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
        return this;
    }

    /**
     * 获取结构化数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        Object value = this.data.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 生成消息ID 
     * 
     */

    TODO: 分布式id
    private static String generateMessageId() {
        return "MSG-" + System.currentTimeMillis() + "-" +
                String.format("%06d", (int) (Math.random() * 1000000));
    }

    /**
     * 创建响应消息
     */
    public AgentMessage createResponse(String responseContent) {
        return AgentMessage.builder()
                .messageId(generateMessageId())
                .sender(this.receiver) // 接收者变成发送者
                .receiver(this.sender) // 发送者变成接收者
                .messageType(MessageType.TASK_RESULT)
                .content(responseContent)
                .parentMessageId(this.messageId)
                .sessionId(this.sessionId)
                .requiresResponse(false)
                .build();
    }

    /**
     * 判断是否需要立即处理（高优先级消息）
     */
    public boolean isUrgent() {
        return this.priority >= 8;
    }
}
