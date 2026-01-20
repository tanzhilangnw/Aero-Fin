package com.aerofin.controller;

import com.aerofin.exception.AeroFinException;
import com.aerofin.exception.ConversationNotFoundException;
import com.aerofin.exception.ToolTimeoutException;
import com.aerofin.exception.VectorStoreException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 统一处理系统异常，返回友好的错误响应
 * <p>
 * 面试亮点：
 * - @RestControllerAdvice 统一异常处理
 * - 分类处理不同异常类型
 * - 返回结构化错误信息
 * - 记录异常日志
 *
 * @author Aero-Fin Team
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常（AeroFinException）
     */
    @ExceptionHandler(AeroFinException.class)
    public ResponseEntity<ErrorResponse> handleAeroFinException(AeroFinException ex) {
        log.error("Business exception: code={}, message={}", ex.getErrorCode(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Business Error")
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理工具超时异常
     */
    @ExceptionHandler(ToolTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleToolTimeoutException(ToolTimeoutException ex) {
        log.error("Tool timeout: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.REQUEST_TIMEOUT.value())
                .error("Tool Timeout")
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .build();

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }

    /**
     * 处理向量存储异常
     */
    @ExceptionHandler(VectorStoreException.class)
    public ResponseEntity<ErrorResponse> handleVectorStoreException(VectorStoreException ex) {
        log.error("Vector store exception: {}", ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Vector Store Error")
                .message("向量检索服务异常，请稍后重试")
                .errorCode(ex.getErrorCode())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理会话不存在异常
     */
    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConversationNotFoundException(ConversationNotFoundException ex) {
        log.warn("Conversation not found: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Conversation Not Found")
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 处理参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("请求参数校验失败")
                .errorCode("VALIDATION_ERROR")
                .details(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected exception", ex);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("系统内部错误，请联系管理员")
                .errorCode("INTERNAL_ERROR")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 错误响应 DTO
     */
    @lombok.Data
    @lombok.Builder
    private static class ErrorResponse {
        private LocalDateTime timestamp;
        private Integer status;
        private String error;
        private String message;
        private String errorCode;
        private Map<String, String> details;
    }
}
