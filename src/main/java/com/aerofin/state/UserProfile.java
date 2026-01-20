package com.aerofin.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户画像（长期记忆的核心组件）
 * <p>
 * 跨会话的用户数据，包括：
 * 1. 基本信息（demographics）
 * 2. 行为偏好（preferences）
 * 3. 历史交互摘要（interaction history）
 * 4. 风险评估（risk profile）
 * <p>
 * 面试亮点：
 * - 用户行为分析与建模
 * - 个性化推荐基础
 * - 跨会话的上下文延续
 * - 支持冷启动与动态更新
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户基本信息
     */
    private Demographics demographics;

    /**
     * 用户偏好
     */
    @Builder.Default
    private Map<String, Object> preferences = new HashMap<>();

    /**
     * 历史交互统计
     */
    @Builder.Default
    private InteractionStats interactionStats = new InteractionStats();

    /**
     * 风险画像
     */
    private RiskProfile riskProfile;

    /**
     * 最近的查询关键词（用于推荐）
     */
    private List<String> recentQueries;

    /**
     * 用户兴趣标签
     * <p>
     * 例如：["小微企业贷款", "罚息减免", "提前还款"]
     */
    private List<String> interestTags;

    /**
     * 创建时间
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 最后更新时间
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 用户基本信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Demographics {
        /**
         * 年龄段
         */
        private String ageRange;

        /**
         * 职业类型
         */
        private String occupation;

        /**
         * 地区
         */
        private String region;

        /**
         * 用户类型：INDIVIDUAL/ENTERPRISE
         */
        @Builder.Default
        private String userType = "INDIVIDUAL";
    }

    /**
     * 交互统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionStats {
        /**
         * 总会话数
         */
        @Builder.Default
        private Integer totalSessions = 0;

        /**
         * 总消息数
         */
        @Builder.Default
        private Integer totalMessages = 0;

        /**
         * 最常用的工具
         */
        private Map<String, Integer> toolUsageCount;

        /**
         * 最常查询的政策类型
         */
        private Map<String, Integer> policyTypeCount;

        /**
         * 平均会话时长（秒）
         */
        @Builder.Default
        private Long avgSessionDuration = 0L;

        /**
         * 最后交互时间
         */
        private LocalDateTime lastInteractionTime;
    }

    /**
     * 风险画像
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskProfile {
        /**
         * 信用评分（0-1000）
         */
        private Integer creditScore;

        /**
         * 风险等级：LOW/MEDIUM/HIGH
         */
        @Builder.Default
        private String riskLevel = "MEDIUM";

        /**
         * 逾期次数
         */
        @Builder.Default
        private Integer overdueCount = 0;

        /**
         * 历史贷款金额总计
         */
        @Builder.Default
        private Double totalLoanAmount = 0.0;

        /**
         * 是否为VIP客户
         */
        @Builder.Default
        private Boolean isVip = false;
    }

    /**
     * 更新用户偏好
     */
    public void updatePreference(String key, Object value) {
        this.preferences.put(key, value);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 记录工具使用
     */
    public void recordToolUsage(String toolName) {
        if (interactionStats.toolUsageCount == null) {
            interactionStats.toolUsageCount = new HashMap<>();
        }
        interactionStats.toolUsageCount.merge(toolName, 1, Integer::sum);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 增加会话计数
     */
    public void incrementSessionCount() {
        interactionStats.totalSessions++;
        interactionStats.lastInteractionTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 判断是否为活跃用户
     */
    public boolean isActiveUser() {
        if (interactionStats.lastInteractionTime == null) {
            return false;
        }
        long daysSinceLastInteraction = java.time.Duration
                .between(interactionStats.lastInteractionTime, LocalDateTime.now())
                .toDays();
        return daysSinceLastInteraction <= 30; // 30天内有交互视为活跃
    }

    /**
     * 获取最常用的工具
     */
    public String getMostUsedTool() {
        if (interactionStats.toolUsageCount == null || interactionStats.toolUsageCount.isEmpty()) {
            return null;
        }
        return interactionStats.toolUsageCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
