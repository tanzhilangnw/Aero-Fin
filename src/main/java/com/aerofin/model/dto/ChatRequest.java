package com.aerofin.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天请求 DTO
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * 会话ID（可选，不传则创建新会话）
     */
    private String sessionId;

    /**
     * 用户ID（可选）
     */
    private String userId;

    /**
     * 用户消息
     */
    @NotBlank(message = "用户消息不能为空")
    @Size(max = 2000, message = "消息长度不能超过2000字符")
    private String message;

    /**
     * 是否启用流式输出（默认 true）
     */
    @Builder.Default
    private Boolean stream = true;

    /**
     * 是否包含历史上下文（默认 true）
     */
    @Builder.Default
    private Boolean includeHistory = true;
}
