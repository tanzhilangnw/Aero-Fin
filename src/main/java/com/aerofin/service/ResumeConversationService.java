package com.aerofin.service;

import com.aerofin.cache.DistributedCacheManager;
import com.aerofin.memory.LayeredMemoryManager;
import com.aerofin.state.SessionState;
import com.aerofin.state.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 断点续聊服务
 * <p>
 * 核心功能：
 * 1. 会话暂停与恢复
 * 2. 状态快照保存与加载
 * 3. 跨设备会话同步
 * 4. 超时会话清理
 * <p>
 * 面试亮点：
 * - 实现断点续聊（类似 ChatGPT Web 端）
 * - 会话状态序列化与反序列化
 * - 支持跨设备/跨实例恢复
 * - 智能会话恢复（检测上下文是否过期）
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeConversationService {

    private final LayeredMemoryManager memoryManager;
    private final DistributedCacheManager cacheManager;
    private final ObjectMapper objectMapper;

    /**
     * 简单的快照存储（进程内），用于在尚未接入 Redis/DB 时完成断点续聊闭环
     * key: snapshotId，value: SessionSnapshot
     */
    private final Map<String, SessionSnapshot> snapshotStore = new ConcurrentHashMap<>();

    /**
     * 暂停会话（保存快照）
     * <p>
     * 保存内容：
     * 1. 会话状态（SessionState）
     * 2. 短期记忆（最近 N 条消息）
     * 3. 用户画像（UserProfile）
     * 4. 时间戳（用于判断是否过期）
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 快照ID
     */
    public String pauseSession(String sessionId, String userId) {
        log.info("⏸️ Pausing session: sessionId={}, userId={}", sessionId, userId);

        try {
            // 1. 创建会话快照
            SessionSnapshot snapshot = SessionSnapshot.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .pausedAt(LocalDateTime.now())
                    .build();

            // 2. 保存短期记忆快照
            Map<String, Object> memorySnapshot = memoryManager.createSessionSnapshot(sessionId);
            snapshot.setMemorySnapshot(memorySnapshot);

            // 3. 保存会话状态快照
            var sessionState = cacheManager.getSessionState(sessionId);
            sessionState.ifPresent(state -> {
                state.setPaused(true);
                snapshot.setSessionStateSnapshot(state.createSnapshot());
                cacheManager.saveSessionState(state);
            });

            // 4. 保存用户画像快照
            var userProfile = memoryManager.getLongTermMemory(userId);
            snapshot.setUserProfileSnapshot(serializeUserProfile(userProfile));

            // 5. 保存快照到缓存（30 天有效期）
            String snapshotId = "snapshot:" + sessionId;
            // 生产环境保存到 Redis 或数据库，这里先使用内存 Map 完成闭环
            snapshotStore.put(snapshotId, snapshot);

            log.info("✅ Session paused successfully: snapshotId={}", snapshotId);
            return snapshotId;

        } catch (Exception e) {
            log.error("Failed to pause session: sessionId={}", sessionId, e);
            throw new RuntimeException("Failed to pause session", e);
        }
    }

    /**
     * 恢复会话（加载快照）
     * <p>
     * 恢复流程：
     * 1. 加载快照数据
     * 2. 检查快照是否过期（超过 30 天）
     * 3. 恢复会话状态
     * 4. 恢复短期记忆
     * 5. 生成恢复摘要（告知用户上次对话内容）
     *
     * @param snapshotId 快照ID
     * @return 恢复结果
     */
    public ResumeResult resumeSession(String snapshotId) {
        log.info("▶️ Resuming session: snapshotId={}", snapshotId);

        try {
            // 1. 加载快照（生产环境从 Redis/数据库加载）
            SessionSnapshot snapshot = snapshotStore.get(snapshotId);

            if (snapshot == null) {
                return ResumeResult.failure("快照不存在或已过期");
            }

            // 2. 检查快照是否过期（超过 30 天）
            if (isSnapshotExpired(snapshot)) {
                return ResumeResult.failure("会话已过期（超过 30 天），请开启新对话");
            }

            // 3. 恢复会话状态
            if (snapshot.getSessionStateSnapshot() != null) {
                SessionState state = SessionState.fromSnapshot(snapshot.getSessionStateSnapshot());
                cacheManager.saveSessionState(state);
            }

            // 4. 恢复短期记忆
            if (snapshot.getMemorySnapshot() != null) {
                memoryManager.restoreSessionFromSnapshot(snapshot.getMemorySnapshot());
            }

            // 5. 生成恢复摘要
            String summary = generateResumeSummary(snapshot);

            log.info("✅ Session resumed successfully: sessionId={}", snapshot.getSessionId());

            return ResumeResult.success(snapshot.getSessionId(), snapshot.getUserId(), summary);

        } catch (Exception e) {
            log.error("Failed to resume session: snapshotId={}", snapshotId, e);
            return ResumeResult.failure("恢复会话失败：" + e.getMessage());
        }
    }

    /**
     * 检查会话是否可恢复
     *
     * @param sessionId 会话ID
     * @return 是否可恢复
     */
    public boolean canResumeSession(String sessionId) {
        String snapshotId = "snapshot:" + sessionId;
        // 检查快照是否存在
        return snapshotStore.containsKey(snapshotId);
    }

    /**
     * 清理过期会话
     * <p>
     * 定时任务：清理超过 30 天的会话快照
     */
    public void cleanupExpiredSessions() {
        log.info("🧹 Cleaning up expired sessions...");
        snapshotStore.entrySet().removeIf(e -> isSnapshotExpired(e.getValue()));

        log.info("✅ Cleanup completed");
    }

    /**
     * 获取用户的所有可恢复会话
     *
     * @param userId 用户ID
     * @return 可恢复的会话列表
     */
    public java.util.List<SessionSummary> getRecoverableSessions(String userId) {
        java.util.List<SessionSummary> result = new java.util.ArrayList<>();
        snapshotStore.values().forEach(snapshot -> {
            if (userId.equals(snapshot.getUserId()) && !isSnapshotExpired(snapshot)) {
                result.add(SessionSummary.builder()
                        .sessionId(snapshot.getSessionId())
                        .title("会话 " + snapshot.getSessionId())
                        .lastMessageTime(snapshot.getPausedAt())
                        .messageCount(null)
                        .preview(null)
                        .build());
            }
        });
        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查快照是否过期
     */
    private boolean isSnapshotExpired(SessionSnapshot snapshot) {
        if (snapshot.getPausedAt() == null) {
            return false;
        }
        long daysSincePaused = java.time.Duration
                .between(snapshot.getPausedAt(), LocalDateTime.now())
                .toDays();
        return daysSincePaused > 30;
    }

    /**
     * 生成恢复摘要
     */
    private String generateResumeSummary(SessionSnapshot snapshot) {
        StringBuilder summary = new StringBuilder();
        summary.append("欢迎回来！\n\n");
        summary.append(String.format("上次对话时间：%s\n",
                snapshot.getPausedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));

        // 添加中期记忆摘要
        String midTermSummary = memoryManager.getMidTermMemorySummary(snapshot.getSessionId());
        summary.append("\n").append(midTermSummary);

        summary.append("\n请继续您的问题，我会基于之前的对话上下文为您服务。");

        return summary.toString();
    }

    /**
     * 序列化用户画像
     */
    private String serializeUserProfile(UserProfile userProfile) {
        try {
            return objectMapper.writeValueAsString(userProfile);
        } catch (Exception e) {
            log.error("Failed to serialize user profile", e);
            return "{}";
        }
    }

    // ==================== 数据类 ====================

    /**
     * 会话快照
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSnapshot {
        private String sessionId;
        private String userId;
        private LocalDateTime pausedAt;
        private Map<String, Object> memorySnapshot;
        private String sessionStateSnapshot;
        private String userProfileSnapshot;
    }

    /**
     * 恢复结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeResult {
        private Boolean success;
        private String sessionId;
        private String userId;
        private String summary;
        private String errorMessage;

        public static ResumeResult success(String sessionId, String userId, String summary) {
            return ResumeResult.builder()
                    .success(true)
                    .sessionId(sessionId)
                    .userId(userId)
                    .summary(summary)
                    .build();
        }

        public static ResumeResult failure(String errorMessage) {
            return ResumeResult.builder()
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    /**
     * 会话摘要（用于展示可恢复的会话列表）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSummary {
        private String sessionId;
        private String title;
        private LocalDateTime lastMessageTime;
        private Integer messageCount;
        private String preview;
    }
}
