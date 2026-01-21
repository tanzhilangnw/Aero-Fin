package com.aerofin.config;

import com.aerofin.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量检索预加载配置
 * <p>
 * 核心功能：
 * 1. 应用启动时预加载热门政策
 * 2. 将常用查询的结果缓存到内存
 * 3. 减少冷启动时的网络开销
 * <p>
 * 面试亮点：
 * - 冷启动优化
 * - 热门数据预加载
 * - 性能优化实践
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Configuration
@Profile({"prod", "dev"})  // 只在生产和开发环境启用
@RequiredArgsConstructor
public class VectorPreloadConfig implements CommandLineRunner {

    private final VectorSearchService vectorSearchService;

    /**
     * 热门查询列表
     */
    private static final List<String> HOT_QUERIES = List.of(
            "小微企业贷款",
            "首套房贷款",
            "消费贷款",
            "经营贷款",
            "罚息减免",
            "提前还款",
            "贷款逾期",
            "贷款额度",
            "贷款利率",
            "贷款条件"
    );

    @Override
    public void run(String... args) throws Exception {
        if (!isPreloadEnabled()) {
            log.info("向量预加载已禁用，跳过");
            return;
        }

        log.info("🚀 开始预加载热门政策...");
        long startTime = System.currentTimeMillis();

        int successCount = 0;
        for (String query : HOT_QUERIES) {
            try {
                // 执行查询，结果会自动缓存到 VectorSearchService 的缓存中
                List<Document> docs = vectorSearchService.searchRelevantPolicies(query);
                if (!docs.isEmpty()) {
                    successCount++;
                    log.debug("✓ 预加载成功: {} -> {} 条文档", query, docs.size());
                }
            } catch (Exception e) {
                log.warn("✗ 预加载失败: {}, 原因: {}", query, e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ 预加载完成: 成功 {}/{} 个查询, 耗时 {}ms",
                successCount, HOT_QUERIES.size(), duration);
    }

    /**
     * 检查是否启用预加载
     */
    private boolean isPreloadEnabled() {
        String enabled = System.getProperty("vector.preload.enabled", "true");
        return Boolean.parseBoolean(enabled);
    }
}
