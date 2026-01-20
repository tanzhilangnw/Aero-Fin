package com.aerofin.memory;

/**
 * 记忆层级枚举
 * <p>
 * 三层记忆架构：
 * - SHORT_TERM: 工作记忆，保存当前会话最近的消息（滑动窗口）
 * - MID_TERM: 会话记忆，保存当前会话的完整历史 + 关键信息摘要
 * - LONG_TERM: 长期记忆，跨会话的用户画像、偏好、历史交互摘要
 * <p>
 * 面试亮点：
 * - 借鉴人类记忆模型（Working Memory + Short-term Memory + Long-term Memory）
 * - 分层设计，平衡性能与上下文完整性
 * - 支持记忆衰减与重要性评分
 *
 * @author Aero-Fin Team
 */
public enum MemoryLayer {

    /**
     * 短期记忆（工作记忆）
     * <p>
     * 存储位置：内存（Caffeine Cache）
     * 生命周期：会话期间，最近 N 条消息
     * 容量：10-20 条消息
     * 用途：直接传给 LLM 的上下文
     */
    SHORT_TERM("短期记忆", 20, 600),

    /**
     * 中期记忆（会话记忆）
     * <p>
     * 存储位置：Redis + 数据库
     * 生命周期：当前会话，包含完整历史
     * 容量：100-500 条消息
     * 用途：会话摘要、关键信息提取
     */
    MID_TERM("中期记忆", 100, 1800),

    /**
     * 长期记忆（用户画像）
     * <p>
     * 存储位置：数据库 + 向量库
     * 生命周期：永久，跨会话
     * 容量：无限制
     * 用途：用户偏好、历史行为分析、个性化推荐
     */
    LONG_TERM("长期记忆", Integer.MAX_VALUE, -1);

    private final String description;
    private final int maxCapacity;
    private final int ttlSeconds;

    MemoryLayer(String description, int maxCapacity, int ttlSeconds) {
        this.description = description;
        this.maxCapacity = maxCapacity;
        this.ttlSeconds = ttlSeconds;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }
}
