package com.aerofin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * 默认向量存储配置
 * <p>
 * 当 Milvus 未启用时，提供一个空的 VectorStore 实现
 * 用于开发和测试环境
 */
@Slf4j
@Configuration
public class DefaultVectorStoreConfig {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore defaultVectorStore() {
        log.warn("⚠️  Using in-memory VectorStore - Milvus is not available");

        return new VectorStore() {
            @Override
            public void add(List<org.springframework.ai.document.Document> documents) {
                log.debug("VectorStore.add() called with {} documents (in-memory mode)", documents.size());
            }

            @Override
            public java.util.Optional<Boolean> delete(List<String> idList) {
                log.debug("VectorStore.delete() called with {} ids (in-memory mode)", idList.size());
                return java.util.Optional.of(true);
            }

            @Override
            public List<org.springframework.ai.document.Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
                log.debug("VectorStore.similaritySearch() called for query: {} (in-memory mode)", request.getQuery());
                return Collections.emptyList();
            }
        };
    }
}
