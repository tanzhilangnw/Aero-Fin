package com.aerofin.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话历史实体
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    /**
     * 主键
     */
    private Long id;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 消息类型: USER/ASSISTANT/SYSTEM
     */
    private String messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 元数据 (工具调用、思考过程等)
     */
    private String metadata;

    /**
     * Token数量
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
