package com.aerofin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具调用结果 VO
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallResult<T> {

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 执行结果
     */
    private T result;

    /**
     * 执行状态: SUCCESS/FAILURE/TIMEOUT
     */
    private String status;

    /**
     * 执行耗时（毫秒）
     */
    private Long executionTimeMs;

    /**
     * 是否命中缓存
     */
    @Builder.Default
    private Boolean cacheHit = false;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;

    /**
     * 执行时间
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 成功结果工厂方法
     */
    public static <T> ToolCallResult<T> success(String toolName, T result, long executionTimeMs, boolean cacheHit) {
        return ToolCallResult.<T>builder()
                .toolName(toolName)
                .result(result)
                .status("SUCCESS")
                .executionTimeMs(executionTimeMs)
                .cacheHit(cacheHit)
                .build();
    }

    /**
     * 失败结果工厂方法
     */
    public static <T> ToolCallResult<T> failure(String toolName, String errorMessage, long executionTimeMs) {
        return ToolCallResult.<T>builder()
                .toolName(toolName)
                .status("FAILURE")
                .errorMessage(errorMessage)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    /**
     * 超时结果工厂方法
     */
    public static <T> ToolCallResult<T> timeout(String toolName, long executionTimeMs) {
        return ToolCallResult.<T>builder()
                .toolName(toolName)
                .status("TIMEOUT")
                .errorMessage("Tool execution timeout")
                .executionTimeMs(executionTimeMs)
                .build();
    }
}
