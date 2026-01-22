package com.aerofin.config;

import com.aerofin.mcp.tools.LoanCalculatorTool;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

/**
 * Spring AI Function Callback 配置
 * <p>
 * 将 MCP 工具适配为 Spring AI 可识别的函数
 * <p>
 * 面试亮点：
 * 1. 适配器模式：MCP 工具 → Spring Function
 * 2. 函数式编程：使用 java.util.function.Function
 * 3. 自动注册：Spring AI 自动发现 @Bean 函数
 * 4. JSON Schema：通过 @JsonPropertyDescription 描述参数
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FunctionCallbackConfig {

    private final LoanCalculatorTool loanCalculatorTool;

    /**
     * 贷款计算函数
     * <p>
     * Spring AI 通过 @Bean + @Description 自动发现此函数
     */
    @Bean
    @Description("计算贷款月供、总利息等信息。支持等额本息和等额本金两种还款方式。")
    public Function<LoanCalculatorRequest, String> calculateLoan() {
        return request -> {
            try {
                log.info("🔧 Function 'calculateLoan' called with input: principal={}, rate={}, term={}",
                        request.principal, request.annualRate, request.termMonths);

                // 转换请求对象为 MCP 工具输入
                LoanCalculatorTool.LoanInput input = LoanCalculatorTool.LoanInput.builder()
                        .principal(request.principal)
                        .annualRate(request.annualRate)
                        .termMonths(request.termMonths)
                        .repaymentType(request.repaymentType != null ? request.repaymentType : "EQUAL_INSTALLMENT")
                        .build();

                var result = loanCalculatorTool.execute(input);

                if (result.getSuccess()) {
                    log.info("✅ Loan calculation success: {}", result.getData().toFormattedString());
                    return result.getData().toFormattedString();
                } else {
                    log.warn("❌ Loan calculation failed: {}", result.getError());
                    return "计算失败：" + result.getError();
                }
            } catch (Exception e) {
                log.error("Error executing calculateLoan", e);
                return "计算出错：" + e.getMessage();
            }
        };
    }

    /**
     * 政策查询函数（占位实现）
     */
    @Bean
    @Description("查询金融政策信息，返回相关政策的详细信息。")
    public Function<PolicyQueryRequest, String> queryPolicy() {
        return request -> {
            log.info("🔧 Function 'queryPolicy' called with: policyType={}, keyword={}",
                    request.policyType, request.keyword);
            // TODO: 实现真实的政策查询逻辑
            return "【政策查询】\n\n暂未找到相关政策。建议咨询客服了解最新政策信息。";
        };
    }

    /**
     * 罚息减免申请函数（占位实现）
     */
    @Bean
    @Description("申请罚息减免，提交减免申请并获取申请编号。")
    public Function<WaiverRequest, String> applyWaiver() {
        return request -> {
            log.info("🔧 Function 'applyWaiver' called with: account={}, amount={}",
                    request.loanAccountNo, request.amount);
            // TODO: 实现真实的罚息减免逻辑
            return String.format("【罚息减免申请】\n\n" +
                    "申请编号：WAIVER-20260122-%s\n" +
                    "贷款账号：%s\n" +
                    "申请金额：%.2f 元\n" +
                    "申请原因：%s\n\n" +
                    "申请已提交，请在 3-5 个工作日内完成审核。",
                    System.currentTimeMillis() % 100000, request.loanAccountNo, request.amount, request.reason);
        };
    }

    /**
     * 查询减免状态函数（占位实现）
     */
    @Bean
    @Description("查询罚息减免申请的审核状态。")
    public Function<WaiverStatusRequest, String> queryWaiverStatus() {
        return request -> {
            log.info("🔧 Function 'queryWaiverStatus' called with: applicationNo={}", request.applicationNo);
            // TODO: 实现真实的状态查询逻辑
            return String.format("【申请状态查询】\n\n" +
                    "申请编号：%s\n" +
                    "当前状态：待审核\n" +
                    "提交时间：2026-01-22 10:00:00\n\n" +
                    "您的申请正在审核中，请耐心等待。",
                    request.applicationNo);
        };
    }

    // ==================== 请求参数类 ====================

    /**
     * 贷款计算请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanCalculatorRequest {
        @JsonProperty("principal")
        @JsonPropertyDescription("贷款本金（元）")
        private Double principal;

        @JsonProperty("annualRate")
        @JsonPropertyDescription("年利率（如 0.045 表示 4.5%）")
        private Double annualRate;

        @JsonProperty("termMonths")
        @JsonPropertyDescription("贷款期限（月）")
        private Integer termMonths;

        @JsonProperty("repaymentType")
        @JsonPropertyDescription("还款方式：EQUAL_INSTALLMENT（等额本息）或 EQUAL_PRINCIPAL（等额本金）")
        private String repaymentType;
    }

    /**
     * 政策查询请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyQueryRequest {
        @JsonProperty("policyType")
        @JsonPropertyDescription("政策类型（如：个人贷款、小微企业贷款等）")
        private String policyType;

        @JsonProperty("keyword")
        @JsonPropertyDescription("查询关键词")
        private String keyword;
    }

    /**
     * 罚息减免申请请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiverRequest {
        @JsonProperty("loanAccountNo")
        @JsonPropertyDescription("贷款账号")
        private String loanAccountNo;

        @JsonProperty("amount")
        @JsonPropertyDescription("申请减免金额（元）")
        private Double amount;

        @JsonProperty("reason")
        @JsonPropertyDescription("申请原因")
        private String reason;
    }

    /**
     * 减免状态查询请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiverStatusRequest {
        @JsonProperty("applicationNo")
        @JsonPropertyDescription("减免申请编号")
        private String applicationNo;
    }
}
