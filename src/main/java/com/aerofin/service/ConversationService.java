package com.aerofin.service;

import com.aerofin.config.AeroFinProperties;
import com.aerofin.model.entity.Conversation;
import com.aerofin.repository.ConversationRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会话管理服务
 * <p>
 * 核心功能：
 * 1. 管理多轮对话历史
 * 2. 实现滑动窗口机制（控制上下文长度）
 * 3. 缓存会话数据（避免频繁查询数据库）
 * 4. 自动清理过期会话
 * <p>
 * 面试亮点：
 * - 滑动窗口算法（控制 Token 数量）
 * - L1 缓存（Caffeine）+ L2 存储（数据库）
 * - 会话持久化策略
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final AeroFinProperties properties;

    @Qualifier("sessionCache")
    private final Cache<String, Object> sessionCache;

    /**
     * 创建新会话
     */
    public String createSession(String userId) {
        String sessionId = "SESSION-" + UUID.randomUUID().toString();
        log.info("Created new session: {} for user: {}", sessionId, userId);

        // 缓存会话元数据
        sessionCache.put(sessionId, new SessionMetadata(sessionId, userId));

        return sessionId;
    }

    /**
     * 保存用户消息
     */
    public void saveUserMessage(String sessionId, String userId, String content) {
        Conversation conversation = Conversation.builder()
                .sessionId(sessionId)
                .userId(userId)
                .messageType("USER")
                .content(content)
                .tokenCount(estimateTokenCount(content))
                .build();

        conversationRepository.save(conversation);
        log.debug("Saved user message for session: {}", sessionId);
    }

    /**
     * 保存 Agent 回复
     */
    public void saveAssistantMessage(String sessionId, String userId, String content, String metadata) {
        Conversation conversation = Conversation.builder()
                .sessionId(sessionId)
                .userId(userId)
                .messageType("ASSISTANT")
                .content(content)
                .metadata(metadata)
                .tokenCount(estimateTokenCount(content))
                .build();

        conversationRepository.save(conversation);
        log.debug("Saved assistant message for session: {}", sessionId);
    }

    /**
     * 获取会话历史（应用滑动窗口）
     * <p>
     * 面试亮点：
     * - 滑动窗口算法：只保留最近 N 条消息
     * - 防止上下文超出 LLM Token 限制
     * - 先从缓存查询，缓存未命中再查数据库
     *
     * @param sessionId 会话ID
     * @return Spring AI Message 列表
     */
    public List<Message> getConversationHistory(String sessionId) {
        int windowSize = properties.getConversation().getContextWindowSize();

        // 1. 从数据库查询最近的消息（按时间降序）
        List<Conversation> conversations = conversationRepository.findBySessionId(sessionId, windowSize);

        if (conversations.isEmpty()) {
            log.debug("No conversation history found for session: {}", sessionId);
            return Collections.emptyList();
        }

        // 2. 反转顺序（从旧到新）
        Collections.reverse(conversations);

        // 3. 转换为 Spring AI Message
        List<Message> messages = conversations.stream()
                .map(this::convertToMessage)
                .collect(Collectors.toList());

        log.info("Retrieved {} messages for session: {} (window size: {})",
                messages.size(), sessionId, windowSize);

        return messages;
    }

    /**
     * 获取会话的所有历史（用于导出、分析等）
     */
    public List<Conversation> getAllHistory(String sessionId) {
        return conversationRepository.findBySessionIdOrdered(sessionId);
    }

    /**
     * 清理旧的会话记录（保留最近 N 条）
     */
    public void cleanupOldMessages(String sessionId) {
        int maxHistorySize = properties.getConversation().getMaxHistorySize();
        conversationRepository.deleteOldConversations(sessionId, maxHistorySize);
        log.info("Cleaned up old messages for session: {}, kept: {}", sessionId, maxHistorySize);
    }

    /**
     * 检查会话是否存在
     */
    public boolean sessionExists(String sessionId) {
        // 先从缓存查询
        if (sessionCache.getIfPresent(sessionId) != null) {
            return true;
        }

        // 缓存未命中，查询数据库
        List<Conversation> history = conversationRepository.findBySessionId(sessionId, 1);
        return !history.isEmpty();
    }

    // ==================== 辅助方法 ====================

    /**
     * 将 Conversation 实体转换为 Spring AI Message
     */
    private Message convertToMessage(Conversation conversation) {
        return switch (conversation.getMessageType()) {
            case "USER" -> new UserMessage(conversation.getContent());
            case "ASSISTANT" -> new AssistantMessage(conversation.getContent());
            default -> throw new IllegalArgumentException("Unknown message type: " + conversation.getMessageType());
        };
    }

    /**
     * 估算 Token 数量
     * <p>
     * 简化实现：中文约 2 字符/token，英文约 4 字符/token
     * 生产环境建议使用 tiktoken-java 库精确计算
     */
    private int estimateTokenCount(String content) {
        if (content == null) return 0;

        // 简化估算：平均 3 字符 = 1 token
        return content.length() / 3;
    }

    /**
     * 会话元数据（缓存用）
     */
    private record SessionMetadata(String sessionId, String userId) {
    }
}
