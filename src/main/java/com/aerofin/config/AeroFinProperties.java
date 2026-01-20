package com.aerofin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Aero-Fin 自定义配置属性
 *
 * @author Aero-Fin Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "aero-fin")
public class AeroFinProperties {

    private LlmConfig llm = new LlmConfig();
    private CacheConfig cache = new CacheConfig();
    private VectorStoreConfig vectorStore = new VectorStoreConfig();
    private ConversationConfig conversation = new ConversationConfig();
    private ToolsConfig tools = new ToolsConfig();

    @Data
    public static class LlmConfig {
        private String model = "gpt-4";
        private Double temperature = 0.7;
        private Integer maxTokens = 2000;
        private Long streamTimeout = 30000L;
    }

    @Data
    public static class CacheConfig {
        private L1CacheConfig l1 = new L1CacheConfig();
        private BloomFilterConfig bloomFilter = new BloomFilterConfig();

        @Data
        public static class L1CacheConfig {
            private Integer ttlSeconds = 600;
            private Long maxSize = 10000L;
            private Integer initialCapacity = 100;
        }

        @Data
        public static class BloomFilterConfig {
            private Long expectedInsertions = 100000L;
            private Double fpp = 0.01;
        }
    }

    @Data
    public static class VectorStoreConfig {
        private Integer embeddingDimension = 1536;
        private Integer topK = 5;
        private Double similarityThreshold = 0.7;
        private Boolean cacheEnabled = true;
    }

    @Data
    public static class ConversationConfig {
        private Integer maxHistorySize = 20;
        private Integer contextWindowSize = 10;
        private Integer sessionTimeoutMinutes = 30;
        private Boolean enableSlidingWindow = true;
    }

    @Data
    public static class ToolsConfig {
        private Long timeoutMillis = 5000L;
        private Boolean enableAsync = true;
        private Boolean enableRetry = true;
        private Integer maxRetryAttempts = 3;
        private Long retryDelayMillis = 1000L;
    }
}
