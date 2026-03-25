package com.aerofin.service;

import com.aerofin.exception.VectorStoreException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RAG ETL 服务
 * <p>
 * 核心功能：
 * 1. 从文件系统提取（Extract）政策文档
 * 2. 转换（Transform）文档为可用于向量化的格式
 * 3. 加载（Load）文档到 Milvus 向量数据库
 * 4. 使用 Redis 实现断点续传
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEtlService {

    private final VectorSearchService vectorSearchService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    private static final String PROCESSED_FILES_KEY = "rag:etl:processed_files";

    /**
     * 执行ETL流程
     *
     * @return 本次处理的文件数量
     * @throws IOException 如果文件读取失败
     */
    public int runEtl() throws Exception {
        return Timer.builder("aerofin.etl.run.duration")
            .description("Duration of the RAG ETL process")
            .register(meterRegistry)
            .recordCallable(() -> {
                log.info("🚀 Starting RAG ETL process...");

                // 1. 获取已处理的文件列表
                Set<Object> processedFiles = redisTemplate.opsForSet().members(PROCESSED_FILES_KEY);
                log.info("Found {} already processed files in Redis.", processedFiles.size());

                // 2. 扫描政策文件夹
                File policyDir = ResourceUtils.getFile("classpath:policies");
                List<Path> filesToProcess;
                try (Stream<Path> stream = Files.walk(policyDir.toPath())) {
                    filesToProcess = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !processedFiles.contains(path.getFileName().toString()))
                        .collect(Collectors.toList());
                }

                if (filesToProcess.isEmpty()) {
                    log.info("✅ No new policy documents to process. ETL finished.");
                    return 0;
                }

                log.info("Found {} new documents to process.", filesToProcess.size());

                // 3. 处理每个新文件
                for (Path filePath : filesToProcess) {
                    processFile(filePath);
                }

                Counter.builder("aerofin.etl.files.processed")
                    .description("Number of files processed by the RAG ETL")
                    .register(meterRegistry)
                    .increment(filesToProcess.size());

                log.info("✅ RAG ETL process finished successfully.");
                return filesToProcess.size();
            });
    }

    /**
     * 处理单个文件：读取、分块、向量化、存储
     *
     * @param filePath 文件路径
     * @throws IOException
     */
    private void processFile(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        log.info("Processing file: {}", fileName);

        try {
            // 1. 读取文件内容
            TextReader textReader = new TextReader(new FileSystemResource(filePath));
            List<Document> documents = textReader.get();

            // 在这里可以添加更复杂的文本分割逻辑（Transform）
            // 例如，按段落、句子或固定长度分割
            // Spring AI 提供了 TextSplitter 接口，如 TokenTextSplitter

            // 2. 加载到向量数据库
            vectorSearchService.addDocuments(documents);

            // 3. 标记为已处理
            redisTemplate.opsForSet().add(PROCESSED_FILES_KEY, fileName);
            log.info("Successfully processed and loaded document: {}", fileName);

        } catch (Exception e) {
            log.error("Failed to process file: {}", fileName, e);
            // 根据策略决定是否抛出异常中断ETL
            throw new VectorStoreException("Failed during ETL for file: " + fileName, e);
        }
    }

    /**
     * 重置ETL状态（清除Redis记录）
     */
    public void resetEtlStatus() {
        redisTemplate.delete(PROCESSED_FILES_KEY);
        log.warn("🔥 RAG ETL status has been reset. All files will be re-processed on next run.");
    }
}
