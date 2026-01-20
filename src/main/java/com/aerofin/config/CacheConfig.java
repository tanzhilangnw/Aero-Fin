package com.aerofin.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 缓存配置
 * <p>
 * 实现多级缓存策略：
 * - L1 缓存：Caffeine（进程内高速缓存）
 * - 布隆过滤器：防止缓存穿透
 * <p>
 * 面试亮点：
 * 1. Caffeine 性能优于 Guava Cache (基于 Window TinyLFU 算法)
 * 2. 布隆过滤器解决缓存穿透问题
 * 3. 可配置的过期策略和容量限制
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final AeroFinProperties properties;

    /**
     * 工具调用结果缓存 (Caffeine L1)
     * <p>
     * 用于缓存耗时的工具调用结果，如：
     * - calculateLoan: 等额本息计算
     * - queryPolicy: 政策查询
     * <p>
     * Key: 工具名称 + 参数的 Hash 值
     * Value: 工具调用结果
     */
    @Bean("toolResultCache")
    public Cache<String, Object> toolResultCache() {
        var cacheConfig = properties.getCache().getL1();

        Cache<String, Object> cache = Caffeine.newBuilder()
                .maximumSize(cacheConfig.getMaxSize())
                .initialCapacity(cacheConfig.getInitialCapacity())
                .expireAfterWrite(Duration.ofSeconds(cacheConfig.getTtlSeconds()))
                // 启用统计（用于监控缓存命中率）
                .recordStats()
                .build();

        log.info("Initialized Caffeine cache for tool results: maxSize={}, ttl={}s",
                cacheConfig.getMaxSize(), cacheConfig.getTtlSeconds());

        return cache;
    }

    /**
     * 向量检索结果缓存
     * <p>
     * 用于缓存向量相似度检索结果
     * Key: 查询向量的 Hash 值
     * Value: Top-K 检索结果
     */
    @Bean("vectorSearchCache")
    public Cache<String, Object> vectorSearchCache() {
        var cacheConfig = properties.getCache().getL1();

        Cache<String, Object> cache = Caffeine.newBuilder()
                .maximumSize(cacheConfig.getMaxSize())
                .initialCapacity(cacheConfig.getInitialCapacity())
                .expireAfterWrite(Duration.ofSeconds(cacheConfig.getTtlSeconds()))
                .recordStats()
                .build();

        log.info("Initialized Caffeine cache for vector search results");

        return cache;
    }

    /**
     * 布隆过滤器（防止缓存穿透）
     * <p>
     * 使用场景：
     * 1. 检查政策编码是否存在，避免无效查询打到数据库
     * 2. 检查工具名称是否合法
     * <p>
     * 面试亮点：
     * - 使用 Guava 的 BloomFilter 实现
     * - FPP (False Positive Probability) 设置为 1%
     * - 空间换时间，大幅减少无效数据库查询
     */
    @Bean("policyCacheBloomFilter")
    public BloomFilter<String> policyCacheBloomFilter() {
        var bloomConfig = properties.getCache().getBloomFilter();

        BloomFilter<String> bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                bloomConfig.getExpectedInsertions(),
                bloomConfig.getFpp()
        );

        log.info("Initialized Bloom Filter: expectedInsertions={}, fpp={}",
                bloomConfig.getExpectedInsertions(), bloomConfig.getFpp());

        return bloomFilter;
    }

    /**
     * 会话缓存
     * <p>
     * 用于缓存用户会话信息（避免频繁查询数据库）
     * Key: sessionId
     * Value: Conversation 对象
     */
    @Bean("sessionCache")
    public Cache<String, Object> sessionCache() {
        var sessionTimeout = properties.getConversation().getSessionTimeoutMinutes();

        Cache<String, Object> cache = Caffeine.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(Duration.ofMinutes(sessionTimeout))
                .recordStats()
                .build();

        log.info("Initialized session cache: timeout={}min", sessionTimeout);

        return cache;
    }
}
