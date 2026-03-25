package com.aerofin.cache;

import com.aerofin.state.SessionState;
import com.aerofin.state.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 分布式缓存管理器（L1 + L2 缓存架构）
 * <p>
 * 架构设计：
 * - **L1 缓存**：Caffeine（进程内，超快速）
 * - **L2 缓存**：Redis（分布式，可共享）
 * <p>
 * 核心功能：
 * 1. 多级缓存读取（L1 → L2 → 数据源）
 * 2. 缓存写入穿透（同时写入 L1 和 L2）
 * 3. 缓存失效策略（TTL + LRU）
 * 4. 缓存预热与更新
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedCacheManager {

    @Qualifier("toolResultCache")
    private final Cache<String, Object> l1Cache;

    @Qualifier("sessionCache")
    private final Cache<String, Object> sessionL1Cache;

    private final ObjectMapper objectMapper;

    @Qualifier("redisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存键前缀
     */
    private static final String SESSION_STATE_PREFIX = "session:state:";
    private static final String USER_PROFILE_PREFIX = "user:profile:";
    private static final String TOOL_RESULT_PREFIX = "tool:result:";
    private static final String AI_ANALYSIS_PREFIX = "ai:analysis:";

    // ==================== 会话状态缓存 ====================

    public Optional<SessionState> getSessionState(String sessionId) {
        String cacheKey = SESSION_STATE_PREFIX + sessionId;

        // 1. 查询 L1 缓存
        SessionState l1Result = (SessionState) sessionL1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.debug("✅ L1 缓存命中: 会话状态 {}", sessionId);
            return Optional.of(l1Result);
        }

        // 2. 查询 L2 缓存（Redis）
        try {
            SessionState l2Result = (SessionState) redisTemplate.opsForValue().get(cacheKey);
            if (l2Result != null) {
                log.debug("✅ L2 缓存命中: 会话状态 {}", sessionId);
                // 回填 L1 缓存
                sessionL1Cache.put(cacheKey, l2Result);
                return Optional.of(l2Result);
            }
        } catch (Exception e) {
            log.error("❌ 查询 L2 缓存失败: 会话状态 {}", sessionId, e);
        }

        log.debug("❌ 缓存未命中: 会话状态 {}", sessionId);
        return Optional.empty();
    }

    public void saveSessionState(SessionState sessionState) {
        String cacheKey = SESSION_STATE_PREFIX + sessionState.getSessionId();

        // 1. 写入 L1 缓存
        sessionL1Cache.put(cacheKey, sessionState);

        // 2. 写入 L2 缓存（Redis）
        try {
            redisTemplate.opsForValue().set(cacheKey, sessionState, Duration.ofMinutes(30));
        } catch (Exception e) {
            log.error("❌ 写入 L2 缓存失败: 会话状态 {}", sessionState.getSessionId(), e);
        }

        log.debug("💾 已保存会话状态到缓存: {}", sessionState.getSessionId());
    }

    public void deleteSessionState(String sessionId) {
        String cacheKey = SESSION_STATE_PREFIX + sessionId;
        sessionL1Cache.invalidate(cacheKey);
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.error("❌ 删除 L2 缓存失败: 会话状态 {}", sessionId, e);
        }
        log.debug("🗑️ 已从缓存中删除会话状态: {}", sessionId);
    }

    // ==================== 用户画像缓存 ====================

    public Optional<UserProfile> getUserProfile(String userId) {
        String cacheKey = USER_PROFILE_PREFIX + userId;

        UserProfile l1Result = (UserProfile) l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.debug("✅ L1 缓存命中: 用户画像 {}", userId);
            return Optional.of(l1Result);
        }

        try {
            UserProfile l2Result = (UserProfile) redisTemplate.opsForValue().get(cacheKey);
            if (l2Result != null) {
                log.debug("✅ L2 缓存命中: 用户画像 {}", userId);
                l1Cache.put(cacheKey, l2Result);
                return Optional.of(l2Result);
            }
        } catch (Exception e) {
            log.error("❌ 查询 L2 缓存失败: 用户画像 {}", userId, e);
        }

        return Optional.empty();
    }

    public void saveUserProfile(UserProfile userProfile) {
        String cacheKey = USER_PROFILE_PREFIX + userProfile.getUserId();
        l1Cache.put(cacheKey, userProfile);
        try {
            redisTemplate.opsForValue().set(cacheKey, userProfile);
        } catch (Exception e) {
            log.error("❌ 写入 L2 缓存失败: 用户画像 {}", userProfile.getUserId(), e);
        }
        log.debug("💾 已保存用户画像到缓存: {}", userProfile.getUserId());
    }

    // ==================== 工具结果缓存 ====================

    public Optional<Object> getToolResult(String toolName, String argsHash) {
        String cacheKey = TOOL_RESULT_PREFIX + toolName + ":" + argsHash;

        Object l1Result = l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.debug("✅ L1 缓存命中: 工具结果 {}", toolName);
            return Optional.of(l1Result);
        }

        try {
            Object l2Result = redisTemplate.opsForValue().get(cacheKey);
            if (l2Result != null) {
                log.debug("✅ L2 缓存命中: 工具结果 {}", toolName);
                l1Cache.put(cacheKey, l2Result);
                return Optional.of(l2Result);
            }
        } catch (Exception e) {
            log.error("❌ 查询 L2 缓存失败: 工具结果 {}", toolName, e);
        }

        return Optional.empty();
    }

    public void saveToolResult(String toolName, String argsHash, Object result) {
        String cacheKey = TOOL_RESULT_PREFIX + toolName + ":" + argsHash;
        l1Cache.put(cacheKey, result);
        try {
            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(10));
        } catch (Exception e) {
            log.error("❌ 写入 L2 缓存失败: 工具结果 {}", toolName, e);
        }
        log.debug("💾 已保存工具结果到缓存: {}", toolName);
    }

    // ==================== AI 分析结果缓存 ====================

    public Optional<Object> getAiAnalysisResult(String analysisType, String contentHash) {
        String cacheKey = AI_ANALYSIS_PREFIX + analysisType + ":" + contentHash;

        Object l1Result = l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.debug("✅ L1 缓存命中: AI 分析 {}", analysisType);
            return Optional.of(l1Result);
        }

        try {
            Object l2Result = redisTemplate.opsForValue().get(cacheKey);
            if (l2Result != null) {
                log.debug("✅ L2 缓存命中: AI 分析 {}", analysisType);
                l1Cache.put(cacheKey, l2Result);
                return Optional.of(l2Result);
            }
        } catch (Exception e) {
            log.error("❌ 查询 L2 缓存失败: AI 分析 {}", analysisType, e);
        }

        return Optional.empty();
    }

    public void saveAiAnalysisResult(String analysisType, String contentHash, Object result) {
        String cacheKey = AI_ANALYSIS_PREFIX + analysisType + ":" + contentHash;
        l1Cache.put(cacheKey, result);
        try {
            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(30));
        } catch (Exception e) {
            log.error("❌ 写入 L2 缓存失败: AI 分析 {}", analysisType, e);
        }
        log.debug("💾 已保存AI分析结果到缓存: {}", analysisType);
    }

    // ==================== 缓存管理 ====================

    public void warmupCache() {
        log.info("🔥 开始缓存预热...");
        // 预热用户画像（活跃用户）
        // List<UserProfile> activeUsers = userProfileRepository.findActiveUsers();
        // activeUsers.forEach(this::saveUserProfile);
        // 预热常用政策
        // List<Policy> hotPolicies = policyRepository.findHotPolicies();
        // hotPolicies.forEach(policy -> {...});
        log.info("✅ 缓存预热完成");
    }

    public void clearAllCaches() {
        l1Cache.invalidateAll();
        sessionL1Cache.invalidateAll();
        try {
            var keys = redisTemplate.keys("*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("❌ 清空 L2 缓存失败", e);
        }
        log.warn("⚠️ 所有缓存已清空");
    }

    public CacheStatistics getStatistics() {
        var l1Stats = l1Cache.stats();
        var sessionStats = sessionL1Cache.stats();
        return CacheStatistics.builder()
                .l1HitRate(l1Stats.hitRate())
                .l1EvictionCount(l1Stats.evictionCount())
                .sessionL1HitRate(sessionStats.hitRate())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class CacheStatistics {
        private Double l1HitRate;
        private Long l1EvictionCount;
        private Double sessionL1HitRate;
        // 可添加 L2（Redis）统计
    }
}
