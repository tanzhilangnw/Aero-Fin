package com.aerofin.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应 DTO
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 响应内容
     */
    private String content;

    /**
     * 是否结束
     */
    @Builder.Default
    private Boolean finished = false;

    /**
     * Token使用量
     */
    private Integer tokenUsage;

    /**
     * 是否使用了工具调用
     */
    @Builder.Default
    private Boolean usedTools = false;

    /**
     * 工具调用信息（调试用）
     */
    private String toolCallInfo;
}
