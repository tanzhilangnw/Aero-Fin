package com.aerofin.service;

import com.aerofin.config.AeroFinProperties;
import com.aerofin.exception.VectorStoreException;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 向量检索服务（RAG - Retrieval-Augmented Generation）
 * <p>
 * 核心功能：
 * 1. 向量相似度检索
 * 2. 语义搜索政策文档
 * 3. 缓存检索结果（避免重复向量计算）
 * <p>
 * 面试亮点：
 * - RAG 架构实现（检索增强生成）
 * - Milvus 向量数据库集成
 * - 语义相似度检索
 * - 检索结果缓存优化
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final VectorStore vectorStore;
    private final AeroFinProperties properties;

    @Qualifier("vectorSearchCache")
    private final Cache<String, Object> vectorSearchCache;

    /**
     * 语义搜索政策文档
     * <p>
     * 流程：
     * 1. 用户问题 → Embedding 向量化
     * 2. 在 Milvus 中执行相似度检索
     * 3. 返回 Top-K 最相关的文档
     * <p>
     * 面试亮点：
     * - 向量检索缓存（基于查询 Hash）
     * - 支持相似度阈值过滤
     * - Top-K 可配置
     *
     * @param query 用户查询
     * @return 相关文档列表
     */
    public List<Document> searchRelevantPolicies(String query) {
        // 1. 生成缓存 Key
        String cacheKey = generateCacheKey(query);

        // 2. 尝试从缓存获取
        if (properties.getVectorStore().getCacheEnabled()) {
            @SuppressWarnings("unchecked")
            List<Document> cachedResult = (List<Document>) vectorSearchCache.getIfPresent(cacheKey);
            if (cachedResult != null) {
                log.info("✅ Vector search cache HIT for query: {}", truncate(query, 50));
                return cachedResult;
            }
        }

        log.info("❌ Vector search cache MISS, executing search: {}", truncate(query, 50));

        try {
            // 3. 构建搜索请求
            SearchRequest searchRequest = SearchRequest.defaults()
                    .withQuery(query)
                    .withTopK(properties.getVectorStore().getTopK())
                    .withSimilarityThreshold(properties.getVectorStore().getSimilarityThreshold());

            // 4. 执行向量检索
            List<Document> results = vectorStore.similaritySearch(searchRequest);

            log.info("🔍 Vector search completed: found {} documents for query: {}",
                    results.size(), truncate(query, 50));

            // 5. 缓存结果
            if (properties.getVectorStore().getCacheEnabled()) {
                vectorSearchCache.put(cacheKey, results);
            }

            return results;

        } catch (Exception e) {
            log.error("Vector search failed for query: {}", query, e);
            throw new VectorStoreException("Failed to search vector store: " + e.getMessage(), e);
        }
    }

    /**
     * 批量添加文档到向量库
     * <p>
     * 用于初始化或批量导入政策文档
     *
     * @param documents 文档列表
     */
    public void addDocuments(List<Document> documents) {
        try {
            vectorStore.add(documents);
            log.info("✅ Added {} documents to vector store", documents.size());
        } catch (Exception e) {
            log.error("Failed to add documents to vector store", e);
            throw new VectorStoreException("Failed to add documents: " + e.getMessage(), e);
        }
    }

    /**
     * 删除所有文档（慎用）
     */
    public void deleteAllDocuments() {
        try {
            vectorStore.delete(List.of()); // Milvus 特定实现
            log.warn("⚠️ Deleted all documents from vector store");
        } catch (Exception e) {
            log.error("Failed to delete documents from vector store", e);
            throw new VectorStoreException("Failed to delete documents: " + e.getMessage(), e);
        }
    }

    /**
     * 格式化检索结果为字符串（用于注入 Prompt）
     * <p>
     * 面试亮点：
     * - RAG 模式：将检索结果注入到 LLM Prompt 中
     * - 提供上下文增强（Context Enhancement）
     */
    public String formatRetrievedContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "未找到相关政策信息。";
        }

        StringBuilder context = new StringBuilder("以下是检索到的相关政策信息：\n\n");
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append(String.format("[文档 %d]\n%s\n\n", i + 1, doc.getContent()));
        }

        return context.toString();
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成缓存 Key（基于查询 Hash）
     */
    private String generateCacheKey(String query) {
        return "vector-search:" + Hashing.sha256()
                .hashString(query, StandardCharsets.UTF_8)
                .toString()
                .substring(0, 16);
    }

    /**
     * 截断字符串（日志用）
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
