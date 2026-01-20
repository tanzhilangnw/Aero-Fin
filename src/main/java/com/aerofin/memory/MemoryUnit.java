package com.aerofin.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记忆单元
 * <p>
 * 核心设计：
 * 1. 重要性评分（importance）：决定记忆是否需要保留到长期记忆
 * 2. 访问频率（accessCount）：影响记忆的优先级
 * 3. 衰减因子（decayFactor）：模拟人类记忆遗忘曲线
 * 4. 元数据（metadata）：存储额外信息（如情感、意图）
 * <p>
 * 面试亮点：
 * - 模拟 Ebbinghaus 遗忘曲线
 * - 支持记忆重要性评分（类似 MemGPT）
 * - 支持记忆检索与激活
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryUnit {

    /**
     * 记忆ID（唯一标识）
     */
    private String memoryId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 记忆层级
     */
    private MemoryLayer layer;

    /**
     * 消息类型：USER/ASSISTANT/SYSTEM
     */
    private String messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 重要性评分（0.0 - 1.0）
     * <p>
     * 评分规则：
     * - 0.0-0.3: 普通对话，可被快速遗忘
     * - 0.3-0.7: 中等重要，需要保留到中期记忆
     * - 0.7-1.0: 高度重要，保存到长期记忆（如用户偏好、关键决策）
     */
    @Builder.Default
    private Double importance = 0.5;

    /**
     * 访问次数（影响记忆优先级）
     */
    @Builder.Default
    private Integer accessCount = 0;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessTime;

    /**
     * 创建时间
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 衰减因子（0.0 - 1.0）
     * <p>
     * 基于 Ebbinghaus 遗忘曲线：
     * R = e^(-t/S)
     * R: 记忆强度, t: 时间间隔, S: 记忆稳定性
     */
    @Builder.Default
    private Double decayFactor = 1.0;

    /**
     * 元数据（扩展信息）
     * <p>
     * 可能包含：
     * - sentiment: 情感分析结果（positive/negative/neutral）
     * - intent: 用户意图（loan_inquiry/policy_query/application）
     * - entities: 提取的实体（金额、日期、政策编码）
     * - keywords: 关键词列表
     */
    private Map<String, Object> metadata;

    /**
     * 向量表示（用于语义检索）
     */
    private float[] embedding;

    /**
     * 是否已归档到长期记忆
     */
    @Builder.Default
    private Boolean archived = false;

    /**
     * 计算当前记忆强度
     * <p>
     * 基于 Ebbinghaus 遗忘曲线：
     * memoryStrength = importance * decayFactor * log(accessCount + 1)
     */
    public double calculateMemoryStrength() {
        double accessBonus = Math.log(accessCount + 1) / Math.log(10); // 归一化到 0-1
        return importance * decayFactor * (1 + accessBonus * 0.2);
    }

    /**
     * 更新衰减因子
     * <p>
     * 衰减公式：decayFactor = e^(-timeSinceCreation / 86400)
     * 假设记忆在 24 小时后衰减到 37%（e^-1）
     */
    public void updateDecayFactor() {
        long secondsSinceCreation = java.time.Duration.between(createdAt, LocalDateTime.now()).getSeconds();
        this.decayFactor = Math.exp(-secondsSinceCreation / 86400.0);
    }

    /**
     * 激活记忆（被访问时调用）
     */
    public void activate() {
        this.accessCount++;
        this.lastAccessTime = LocalDateTime.now();
        // 访问时重置部分衰减
        this.decayFactor = Math.min(1.0, this.decayFactor + 0.1);
    }
}
