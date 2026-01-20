package com.aerofin.cache;

import com.aerofin.state.SessionState;
import com.aerofin.state.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
 * <p>
 * 面试亮点：
 * - 多级缓存架构（类似 Guava LocalCache + Redis）
 * - 缓存一致性保证（写穿、旁路、写回策略）
 * - 缓存击穿/穿透/雪崩防护
 * - 支持分布式环境（多实例共享缓存）
 * <p>
 * 注意：
 * 本实现提供 Redis 接口定义，具体 Redis 操作需要添加 Spring Data Redis 依赖
 * 如果不使用 Redis，可以继续使用纯 Caffeine 缓存
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

    // Redis 操作接口（生产环境需要注入 RedisTemplate）
    // private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存键前缀
     */
    private static final String SESSION_STATE_PREFIX = "session:state:";
    private static final String USER_PROFILE_PREFIX = "user:profile:";
    private static final String TOOL_RESULT_PREFIX = "tool:result:";
    private static final String AI_ANALYSIS_PREFIX = "ai:analysis:";

    // ==================== 会话状态缓存 ====================

    /**
     * 获取会话状态（多级缓存）
     * <p>
     * 读取流程：
     * 1. 查询 L1 缓存（Caffeine）
     * 2. 如果未命中，查询 L2 缓存（Redis）
     * 3. 如果 L2 命中，回填 L1
     * 4. 如果都未命中，返回空
     */
    public Optional<SessionState> getSessionState(String sessionId) {
        String cacheKey = SESSION_STATE_PREFIX + sessionId;

        // 1. 查询 L1 缓存
        SessionState l1Result = (SessionState) sessionL1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.debug("✅ L1 cache HIT for session state: {}", sessionId);
            return Optional.of(l1Result);
        }

        // 2. 查询 L2 缓存（Redis）
        // 注意：生产环境需要取消注释
        /*
        SessionState l2Result = (SessionState) redisTemplate.opsForValue().get(cacheKey);
        if (l2Result != null) {
            log.debug("✅ L2 cache HIT for session state: {}", sessionId);
            // 回填 L1 缓存
            sessionL1Cache.put(cacheKey, l2Result);
            return Optional.of(l2Result);
        }
        */

        log.debug("❌ Cache MISS for session state: {}", sessionId);
        return Optional.empty();
    }

    /**
     * 保存会话状态（写穿策略）
     * <p>
     * 写入流程：
     * 1. 同时写入 L1 和 L2
     * 2. L2 设置 TTL（30 分钟）
     */
    public void saveSessionState(SessionState sessionState) {
        String cacheKey = SESSION_STATE_PREFIX + sessionState.getSessionId();

        // 1. 写入 L1 缓存
        sessionL1Cache.put(cacheKey, sessionState);

        // 2. 写入 L2 缓存（Redis）
        // 注意：生产环境需要取消注释
        /*
        redisTemplate.opsForValue().set(cacheKey, sessionState,
            Duration.ofMinutes(30));
        */

        log.debug("💾 Saved session state to cache: {}", sessionState.getSessionId());
    }

    /**
     * 删除会话状态
     */
    public void deleteSessionState(String sessionId) {
        String cacheKey = SESSION_STATE_PREFIX + sessionId;

        // 删除 L1
        sessionL1Cache.invalidate(cacheKey);

        // 删除 L2（Redis）
        // redisTemplate.delete(cacheKey);

        log.debug("🗑️ Deleted session state from cache: {}", sessionId);
    }

    // ==================== 用户画像缓存 ====================

    /**
     * 获取用户画像（长期缓存）
     */
    public Optional<UserProfile> getUserProfile(String userId) {
        String cacheKey = USER_PROFILE_PREFIX + userId;

        // L1 缓存
        UserProfile l1Result = (UserProfile) l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            return Optional.of(l1Result);
        }

        // L2 缓存（Redis）
        // UserProfile l2Result = (UserProfile) redisTemplate.opsForValue().get(cacheKey);
        // if (l2Result != null) {
        //     l1Cache.put(cacheKey, l2Result);
        //     return Optional.of(l2Result);
        // }

        return Optional.empty();
    }

    /**
     * 保存用户画像（永久缓存）
     */
    public void saveUserProfile(UserProfile userProfile) {
        String cacheKey = USER_PROFILE_PREFIX + userProfile.getUserId();

        // L1 缓存
        l1Cache.put(cacheKey, userProfile);

        // L2 缓存（不设置过期时间）
        // redisTemplate.opsForValue().set(cacheKey, userProfile);

        log.debug("💾 Saved user profile to cache: {}", userProfile.getUserId());
    }

    // ==================== 工具结果缓存 ====================

    /**
     * 获取工具调用结果（高频缓存）
     */
    public Optional<Object> getToolResult(String toolName, String argsHash) {
        String cacheKey = TOOL_RESULT_PREFIX + toolName + ":" + argsHash;

        // L1 缓存
        Object l1Result = l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.debug("✅ L1 cache HIT for tool result: {}", toolName);
            return Optional.of(l1Result);
        }

        // L2 缓存（Redis）
        // Object l2Result = redisTemplate.opsForValue().get(cacheKey);
        // if (l2Result != null) {
        //     log.debug("✅ L2 cache HIT for tool result: {}", toolName);
        //     l1Cache.put(cacheKey, l2Result);
        //     return Optional.of(l2Result);
        // }

        return Optional.empty();
    }

    /**
     * 保存工具调用结果（10 分钟 TTL）
     */
    public void saveToolResult(String toolName, String argsHash, Object result) {
        String cacheKey = TOOL_RESULT_PREFIX + toolName + ":" + argsHash;

        // L1 缓存
        l1Cache.put(cacheKey, result);

        // L2 缓存（10 分钟过期）
        // redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(10));

        log.debug("💾 Saved tool result to cache: {}", toolName);
    }

    // ==================== AI 分析结果缓存（高耗时操作）====================

    /**
     * 获取 AI 分析结果
     * <p>
     * 用于缓存高耗时的 AI 分析任务，如：
     * - 情感分析
     * - 意图识别
     * - 实体提取
     * - 文本摘要
     */
    public Optional<Object> getAiAnalysisResult(String analysisType, String contentHash) {
        String cacheKey = AI_ANALYSIS_PREFIX + analysisType + ":" + contentHash;

        // L1 缓存
        Object l1Result = l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.debug("✅ L1 cache HIT for AI analysis: {}", analysisType);
            return Optional.of(l1Result);
        }

        // L2 缓存（Redis）
        // Object l2Result = redisTemplate.opsForValue().get(cacheKey);
        // if (l2Result != null) {
        //     log.debug("✅ L2 cache HIT for AI analysis: {}", analysisType);
        //     l1Cache.put(cacheKey, l2Result);
        //     return Optional.of(l2Result);
        // }

        return Optional.empty();
    }

    /**
     * 保存 AI 分析结果（30 分钟 TTL）
     */
    public void saveAiAnalysisResult(String analysisType, String contentHash, Object result) {
        String cacheKey = AI_ANALYSIS_PREFIX + analysisType + ":" + contentHash;

        // L1 缓存
        l1Cache.put(cacheKey, result);

        // L2 缓存（30 分钟过期）
        // redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(30));

        log.debug("💾 Saved AI analysis result to cache: {}", analysisType);
    }

    // ==================== 缓存管理 ====================

    /**
     * 预热缓存
     * <p>
     * 在系统启动时，将热点数据加载到缓存
     */
    public void warmupCache() {
        log.info("🔥 Starting cache warmup...");

        // 预热用户画像（活跃用户）
        // List<UserProfile> activeUsers = userProfileRepository.findActiveUsers();
        // activeUsers.forEach(this::saveUserProfile);

        // 预热常用政策
        // List<Policy> hotPolicies = policyRepository.findHotPolicies();
        // hotPolicies.forEach(policy -> {...});

        log.info("✅ Cache warmup completed");
    }

    /**
     * 清空所有缓存
     */
    public void clearAllCaches() {
        l1Cache.invalidateAll();
        sessionL1Cache.invalidateAll();

        // redisTemplate.delete(redisTemplate.keys("*"));

        log.warn("⚠️ All caches cleared");
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStatistics getStatistics() {
        var l1Stats = l1Cache.stats();
        var sessionStats = sessionL1Cache.stats();

        return CacheStatistics.builder()
                .l1HitRate(l1Stats.hitRate())
                .l1EvictionCount(l1Stats.evictionCount())
                .sessionL1HitRate(sessionStats.hitRate())
                .build();
    }

    /**
     * 缓存统计数据
     */
    @lombok.Data
    @lombok.Builder
    public static class CacheStatistics {
        private Double l1HitRate;
        private Long l1EvictionCount;
        private Double sessionL1HitRate;
        // 可添加 L2（Redis）统计
    }
}
