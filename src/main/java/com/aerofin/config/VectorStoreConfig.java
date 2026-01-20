package com.aerofin.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 * <p>
 * 配置向量存储和检索功能，用于 RAG (Retrieval-Augmented Generation)
 * <p>
 * 面试亮点：
 * 1. 集成 Milvus 向量数据库（专业的向量检索引擎）
 * 2. 实现语义相似度检索
 * 3. 支持政策文档的向量化存储和查询
 * 4. 使用 Spring AI 的 VectorStore 抽象接口，便于切换向量库
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class VectorStoreConfig {

    private final AeroFinProperties properties;
    private final EmbeddingModel embeddingModel;

    @Value("${spring.ai.vectorstore.milvus.client.host:localhost}")
    private String milvusHost;

    @Value("${spring.ai.vectorstore.milvus.client.port:19530}")
    private Integer milvusPort;

    @Value("${spring.ai.vectorstore.milvus.client.username:root}")
    private String milvusUsername;

    @Value("${spring.ai.vectorstore.milvus.client.password:Milvus}")
    private String milvusPassword;

    @Value("${spring.ai.vectorstore.milvus.database-name:aero_fin}")
    private String databaseName;

    @Value("${spring.ai.vectorstore.milvus.collection-name:financial_policies}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.milvus.embedding-dimension:1536}")
    private Integer embeddingDimension;

    /**
     * 配置 Milvus 客户端
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(milvusHost)
                .withPort(milvusPort)
                .withDatabaseName(databaseName)
                .withAuthorization(milvusUsername, milvusPassword)
                .build();

        MilvusServiceClient client = new MilvusServiceClient(connectParam);

        log.info("Initialized Milvus client: host={}, port={}, database={}",
                milvusHost, milvusPort, databaseName);

        return client;
    }

    /**
     * 配置 Spring AI VectorStore
     * <p>
     * VectorStore 用于：
     * 1. 存储政策文档的向量表示
     * 2. 执行相似度检索（根据用户问题查询相关政策）
     * 3. 支持 RAG 模式（检索增强生成）
     */
    @Bean
    public VectorStore vectorStore(MilvusServiceClient milvusClient) {
        MilvusVectorStore.MilvusVectorStoreConfig config = MilvusVectorStore.MilvusVectorStoreConfig.builder()
                .withCollectionName(collectionName)
                .withDatabaseName(databaseName)
                .withIndexType(MilvusVectorStore.IndexType.IVF_FLAT)
                .withMetricType(MilvusVectorStore.MetricType.COSINE)
                .withEmbeddingDimension(embeddingDimension)
                .build();

        MilvusVectorStore vectorStore = new MilvusVectorStore(
                milvusClient,
                embeddingModel,
                config,
                true // 初始化 Schema
        );

        log.info("Initialized MilvusVectorStore: collection={}, dimension={}",
                collectionName, embeddingDimension);

        return vectorStore;
    }
}
