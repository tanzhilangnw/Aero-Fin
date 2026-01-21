package com.aerofin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.aerofin.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 统一错误响应
 * <p>
 * 所有异常都返回此格式
 * <p>
 * 示例：
 * ```json
 * {
 *   "code": 20001,
 *   "message": "会话不存在",
 *   "detail": "Session ID: abc123",
 *   "traceId": "trace-xyz789",
 *   "path": "/api/chat/stream",
 *   "timestamp": "2024-01-20T15:30:00"
 * }
 * ```
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "错误响应")
public class ErrorResponse {

    @Schema(description = "错误码", example = "20001")
    private Integer code;

    @Schema(description = "错误消息", example = "会话不存在")
    private String message;

    @Schema(description = "详细信息（开发环境显示，生产环境可隐藏）", example = "Session ID: abc123 not found")
    private String detail;

    @Schema(description = "追踪ID（用于链路追踪）", example = "trace-abc123")
    private String traceId;

    @Schema(description = "请求路径", example = "/api/chat/stream")
    private String path;

    @Schema(description = "时间戳", example = "2024-01-20T15:30:00")
    private LocalDateTime timestamp;

    /**
     * 根据错误码创建错误响应
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 根据错误码和详情创建错误响应
     */
    public static ErrorResponse of(ErrorCode errorCode, String detail) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .detail(detail)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 根据错误码、详情和追踪ID创建错误响应
     */
    public static ErrorResponse of(ErrorCode errorCode, String detail, String traceId) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .detail(detail)
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
