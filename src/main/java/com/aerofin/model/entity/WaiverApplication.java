package com.aerofin.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 罚息减免申请实体
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiverApplication {

    /**
     * 主键
     */
    private Long id;

    /**
     * 申请编号
     */
    private String applicationNo;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 贷款账号
     */
    private String loanAccount;

    /**
     * 减免金额
     */
    private BigDecimal waiverAmount;

    /**
     * 申请原因
     */
    private String reason;

    /**
     * 状态: PENDING/APPROVED/REJECTED
     */
    private String status;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 审核人
     */
    private String reviewer;

    /**
     * 审核意见
     */
    private String reviewComment;
}
