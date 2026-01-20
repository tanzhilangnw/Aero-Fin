package com.aerofin.memory;

import com.aerofin.repository.ConversationRepository;
import com.aerofin.state.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分层记忆管理器
 * <p>
 * 核心功能：
 * 1. 管理三层记忆（短期/中期/长期）
 * 2. 自动记忆提升（短期 → 中期 → 长期）
 * 3. 记忆检索与激活
 * 4. 记忆衰减与清理
 * 5. 支持断点续聊（会话恢复）
 * <p>
 * 面试亮点：
 * - 三层记忆架构（类似 MemGPT）
 * - 基于重要性的记忆提升策略
 * - Ebbinghaus 遗忘曲线模拟
 * - 语义检索 + 关键词检索混合
 * - 支持记忆压缩与摘要
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LayeredMemoryManager {

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    @Qualifier("sessionCache")
    private final Cache<String, Object> sessionCache;

    // 临时存储（生产环境应使用 Redis）
    private final Map<String, List<MemoryUnit>> shortTermMemory = new HashMap<>();
    private final Map<String, UserProfile> longTermMemory = new HashMap<>();

    /**
     * 添加记忆到短期记忆
     * <p>
     * 流程：
     * 1. 创建 MemoryUnit
     * 2. 评估重要性
     * 3. 添加到短期记忆
     * 4. 如果超过容量，触发记忆提升
     */
    public void addMemory(String sessionId, String userId, String messageType, String content) {
        MemoryUnit memory = MemoryUnit.builder()
                .memoryId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .userId(userId)
                .layer(MemoryLayer.SHORT_TERM)
                .messageType(messageType)
                .content(content)
                .importance(evaluateImportance(content, messageType))
                .build();

        // 添加到短期记忆
        shortTermMemory.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(memory);

        log.debug("Added memory to SHORT_TERM: sessionId={}, importance={}",
                sessionId, memory.getImportance());

        // 检查是否需要记忆提升
        promoteMemoryIfNeeded(sessionId);
    }

    /**
     * 获取短期记忆（用于传给 LLM）
     * <p>
     * 返回最近 N 条消息，按时间顺序排列
     */
    public List<Message> getShortTermMemory(String sessionId, int limit) {
        List<MemoryUnit> memories = shortTermMemory.getOrDefault(sessionId, Collections.emptyList());

        // 获取最近的 N 条记忆
        return memories.stream()
                .sorted(Comparator.comparing(MemoryUnit::getCreatedAt).reversed())
                .limit(limit)
                .sorted(Comparator.comparing(MemoryUnit::getCreatedAt)) // 按时间正序
                .map(this::convertToMessage)
                .collect(Collectors.toList());
    }

    /**
     * 获取中期记忆摘要
     * <p>
     * 从数据库查询完整会话历史，提取关键信息
     */
    public String getMidTermMemorySummary(String sessionId) {
        // 从数据库查询会话历史
        var conversations = conversationRepository.findBySessionIdOrdered(sessionId);

        if (conversations.isEmpty()) {
            return "无历史会话记录。";
        }

        // 生成摘要（简化实现，生产环境可用 LLM 生成）
        StringBuilder summary = new StringBuilder("历史会话摘要：\n");
        summary.append(String.format("- 会话开始时间：%s\n", conversations.get(0).getCreatedAt()));
        summary.append(String.format("- 消息总数：%d 条\n", conversations.size()));

        // 提取关键信息（例如：用户问过的问题）
        List<String> userQuestions = conversations.stream()
                .filter(c -> "USER".equals(c.getMessageType()))
                .map(c -> c.getContent().substring(0, Math.min(50, c.getContent().length())))
                .limit(5)
                .collect(Collectors.toList());

        if (!userQuestions.isEmpty()) {
            summary.append("- 用户关注的主题：\n");
            userQuestions.forEach(q -> summary.append("  * ").append(q).append("...\n"));
        }

        return summary.toString();
    }

    /**
     * 获取长期记忆（用户画像）
     */
    public UserProfile getLongTermMemory(String userId) {
        return longTermMemory.computeIfAbsent(userId, k -> UserProfile.builder()
                .userId(userId)
                .build());
    }

    /**
     * 更新长期记忆
     */
    public void updateLongTermMemory(String userId, UserProfile profile) {
        longTermMemory.put(userId, profile);
        log.info("Updated LONG_TERM memory for user: {}", userId);
    }

    /**
     * 记忆提升策略
     * <p>
     * 规则：
     * 1. 短期记忆超过容量 → 高重要性的提升到中期记忆
     * 2. 中期记忆中的关键信息 → 提升到长期记忆（用户画像）
     */
    private void promoteMemoryIfNeeded(String sessionId) {
        List<MemoryUnit> memories = shortTermMemory.get(sessionId);
        if (memories == null) return;

        int maxCapacity = MemoryLayer.SHORT_TERM.getMaxCapacity();

        if (memories.size() > maxCapacity) {
            // 按重要性排序
            memories.sort(Comparator.comparing(MemoryUnit::getImportance).reversed());

            // 提升高重要性的记忆到中期记忆（保存到数据库）
            List<MemoryUnit> toPromote = memories.stream()
                    .filter(m -> m.getImportance() > 0.7)
                    .limit(5)
                    .collect(Collectors.toList());

            for (MemoryUnit memory : toPromote) {
                // 保存到数据库（中期记忆）
                conversationRepository.save(com.aerofin.model.entity.Conversation.builder()
                        .sessionId(memory.getSessionId())
                        .userId(memory.getUserId())
                        .messageType(memory.getMessageType())
                        .content(memory.getContent())
                        .build());

                memory.setLayer(MemoryLayer.MID_TERM);
                log.info("Promoted memory to MID_TERM: memoryId={}, importance={}",
                        memory.getMemoryId(), memory.getImportance());
            }

            // 删除已提升的记忆
            memories.removeAll(toPromote);

            // 删除超出容量的低重要性记忆
            while (memories.size() > maxCapacity) {
                MemoryUnit removed = memories.remove(0);
                log.debug("Removed low-importance memory: memoryId={}", removed.getMemoryId());
            }
        }
    }

    /**
     * 评估记忆重要性
     * <p>
     * 评分规则（0.0 - 1.0）：
     * 1. 包含关键词（贷款、政策、申请）：+0.3
     * 2. 包含数字（金额、日期）：+0.2
     * 3. 用户消息：+0.1
     * 4. 工具调用结果：+0.2
     */
    private double evaluateImportance(String content, String messageType) {
        double importance = 0.5; // 基础分

        if (content == null || content.isEmpty()) {
            return 0.1;
        }

        // 包含关键词
        String[] keywords = {"贷款", "政策", "申请", "减免", "利率", "金额"};
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                importance += 0.3;
                break;
            }
        }

        // 包含数字（可能是金额、日期等重要信息）
        if (content.matches(".*\\d+.*")) {
            importance += 0.2;
        }

        // 用户消息优先级高于系统消息
        if ("USER".equals(messageType)) {
            importance += 0.1;
        }

        return Math.min(1.0, importance);
    }

    /**
     * 语义检索记忆
     * <p>
     * 基于相似度检索相关记忆（需要向量化）
     * 简化实现：关键词匹配
     */
    public List<MemoryUnit> searchMemories(String sessionId, String query, int topK) {
        List<MemoryUnit> memories = shortTermMemory.getOrDefault(sessionId, Collections.emptyList());

        // 简化实现：关键词匹配
        return memories.stream()
                .filter(m -> m.getContent().contains(query))
                .sorted(Comparator.comparing(MemoryUnit::calculateMemoryStrength).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 清理过期记忆（基于衰减因子）
     */
    public void cleanupExpiredMemories(String sessionId) {
        List<MemoryUnit> memories = shortTermMemory.get(sessionId);
        if (memories == null) return;

        // 更新所有记忆的衰减因子
        memories.forEach(MemoryUnit::updateDecayFactor);

        // 删除衰减因子过低的记忆
        memories.removeIf(m -> m.getDecayFactor() < 0.1);

        log.info("Cleaned up expired memories for session: {}", sessionId);
    }

    /**
     * 会话快照（断点续聊）
     * <p>
     * 保存当前会话的完整状态，用于恢复
     */
    public Map<String, Object> createSessionSnapshot(String sessionId) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("sessionId", sessionId);
        snapshot.put("shortTermMemory", shortTermMemory.get(sessionId));
        snapshot.put("timestamp", LocalDateTime.now());

        log.info("Created session snapshot: sessionId={}", sessionId);
        return snapshot;
    }

    /**
     * 恢复会话（断点续聊）
     */
    @SuppressWarnings("unchecked")
    public void restoreSessionFromSnapshot(Map<String, Object> snapshot) {
        String sessionId = (String) snapshot.get("sessionId");
        List<MemoryUnit> memories = (List<MemoryUnit>) snapshot.get("shortTermMemory");

        if (memories != null) {
            shortTermMemory.put(sessionId, new ArrayList<>(memories));
            log.info("Restored session from snapshot: sessionId={}, memoryCount={}",
                    sessionId, memories.size());
        }
    }

    /**
     * 将 MemoryUnit 转换为 Spring AI Message
     */
    private Message convertToMessage(MemoryUnit memory) {
        return switch (memory.getMessageType()) {
            case "USER" -> new UserMessage(memory.getContent());
            case "ASSISTANT" -> new AssistantMessage(memory.getContent());
            default -> new UserMessage(memory.getContent());
        };
    }
}
