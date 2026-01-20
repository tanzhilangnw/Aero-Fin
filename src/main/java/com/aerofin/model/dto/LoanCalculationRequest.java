package com.aerofin.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 贷款计算请求 DTO
 *
 * @author Aero-Fin Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanCalculationRequest {

    /**
     * 贷款金额（元）
     */
    private BigDecimal principal;

    /**
     * 年利率（如 0.0385 表示 3.85%）
     */
    private BigDecimal annualRate;

    /**
     * 贷款期限（月）
     */
    private Integer termMonths;
}
