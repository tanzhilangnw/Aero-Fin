package com.aerofin.controller;

import com.aerofin.service.RagEtlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * RAG ETL 控制器
 * <p>
 * 提供手动触发ETL流程的API
 */
@RestController
@RequestMapping("/api/etl")
@RequiredArgsConstructor
public class EtlController {

    private final RagEtlService ragEtlService;

    /**
     * 运行RAG ETL流程
     * <p>
     * 扫描 /resources/policies 文件夹，将新文档加载到Milvus
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runEtl() {
        try {
            int processedCount = ragEtlService.runEtl();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "ETL process completed.",
                "processedFiles", processedCount
            ));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "ETL process failed: " + e.getMessage()
            ));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 重置ETL状态
     * <p>
     * 清除Redis中的处理记录，下次运行时将重新处理所有文件
     */
    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, String>> resetEtlStatus() {
        ragEtlService.resetEtlStatus();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "ETL status has been reset. All files will be re-processed on the next run."
        ));
    }
}
