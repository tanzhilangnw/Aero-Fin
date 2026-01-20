package com.aerofin.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 金融政策实体
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Policy {

    /**
     * 主键
     */
    private Long id;

    /**
     * 政策编码
     */
    private String policyCode;

    /**
     * 政策名称
     */
    private String policyName;

    /**
     * 政策类型: LOAN/WAIVER/INTEREST
     */
    private String policyType;

    /**
     * 政策描述
     */
    private String description;

    /**
     * 政策详细内容
     */
    private String content;

    /**
     * 适用条件 (JSON格式)
     */
    private String conditions;

    /**
     * 生效日期
     */
    private LocalDate effectiveDate;

    /**
     * 失效日期
     */
    private LocalDate expireDate;

    /**
     * 状态: ACTIVE/INACTIVE
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
