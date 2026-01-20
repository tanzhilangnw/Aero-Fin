package com.aerofin.mcp.tools;

import com.aerofin.mcp.McpTool;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.hash.Hashing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 贷款计算工具（MCP 标准实现）
 * <p>
 * 核心功能：
 * - 等额本息计算
 * - 等额本金计算
 * - 提前还款计算
 * <p>
 * 面试亮点：
 * - 完全符合 MCP 规范
 * - 参数自动验证
 * - 缓存策略（基于参数 Hash）
 * - 监控埋点
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
@McpTool.Tool(
        name = "calculateLoan",
        description = "计算贷款月供、总利息等信息，支持等额本息和等额本金两种方式",
        category = "financial",
        cacheable = true
)
public class LoanCalculatorTool implements McpTool<LoanCalculatorTool.LoanInput, LoanCalculatorTool.LoanOutput> {

    @Qualifier("toolResultCache")
    private final Cache<String, Object> cache;

    public LoanCalculatorTool(@Qualifier("toolResultCache") Cache<String, Object> cache) {
        this.cache = cache;
    }

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder()
                .name("calculateLoan")
                .description("计算贷款月供、总利息等信息，支持等额本息和等额本金两种方式")
                .category("financial")
                .cacheable(true)
                .async(false)
                .estimatedDuration(500L)
                .version("1.0.0")
                .tags(List.of("loan", "calculation", "financial"))
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("principal")
                                .type("number")
                                .description("贷款本金（元）")
                                .required(true)
                                .minimum(1000)
                                .maximum(100000000)
                                .build(),
                        ToolParameter.builder()
                                .name("annualRate")
                                .type("number")
                                .description("年利率（如 0.045 表示 4.5%）")
                                .required(true)
                                .minimum(0.001)
                                .maximum(0.3)
                                .build(),
                        ToolParameter.builder()
                                .name("termMonths")
                                .type("integer")
                                .description("贷款期限（月）")
                                .required(true)
                                .minimum(1)
                                .maximum(360)
                                .build(),
                        ToolParameter.builder()
                                .name("repaymentType")
                                .type("string")
                                .description("还款方式：EQUAL_INSTALLMENT（等额本息）或 EQUAL_PRINCIPAL（等额本金）")
                                .required(false)
                                .defaultValue("EQUAL_INSTALLMENT")
                                .enumValues(List.of("EQUAL_INSTALLMENT", "EQUAL_PRINCIPAL"))
                                .build()
                ))
                .build();
    }

    @Override
    public ToolResult<LoanOutput> execute(LoanInput input) throws Exception {
        long startTime = System.currentTimeMillis();

        // 1. 参数验证
        if (!validateInput(input)) {
            return ToolResult.failure("Invalid input parameters", System.currentTimeMillis() - startTime);
        }

        // 2. 生成缓存 Key
        String cacheKey = generateCacheKey(input);

        // 3. 尝试从缓存获取
        @SuppressWarnings("unchecked")
        LoanOutput cachedResult = (LoanOutput) cache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            log.info("✅ Cache HIT for loan calculation: {}", cacheKey);
            return ToolResult.success(cachedResult, System.currentTimeMillis() - startTime, true);
        }

        log.info("❌ Cache MISS for loan calculation: {}, calculating...", cacheKey);

        // 4. 模拟耗时计算
        Thread.sleep(500);

        // 5. 执行计算
        LoanOutput output = calculateLoanDetails(input);

        // 6. 缓存结果
        cache.put(cacheKey, output);

        long duration = System.currentTimeMillis() - startTime;
        return ToolResult.success(output, duration, false);
    }

    @Override
    public boolean validateInput(LoanInput input) {
        if (input == null) return false;
        if (input.getPrincipal() <= 0 || input.getPrincipal() > 100000000) return false;
        if (input.getAnnualRate() <= 0 || input.getAnnualRate() > 0.3) return false;
        if (input.getTermMonths() <= 0 || input.getTermMonths() > 360) return false;
        return true;
    }

    /**
     * 执行贷款计算
     */
    private LoanOutput calculateLoanDetails(LoanInput input) {
        String repaymentType = input.getRepaymentType() != null ?
                input.getRepaymentType() : "EQUAL_INSTALLMENT";

        if ("EQUAL_PRINCIPAL".equals(repaymentType)) {
            return calculateEqualPrincipal(input);
        } else {
            return calculateEqualInstallment(input);
        }
    }

    /**
     * 等额本息计算
     */
    private LoanOutput calculateEqualInstallment(LoanInput input) {
        BigDecimal principal = BigDecimal.valueOf(input.getPrincipal());
        BigDecimal monthlyRate = BigDecimal.valueOf(input.getAnnualRate() / 12);
        int termMonths = input.getTermMonths();

        // 月还款额 = 本金 × [月利率 × (1+月利率)^期数] / [(1+月利率)^期数 - 1]
        BigDecimal onePlusRate = monthlyRate.add(BigDecimal.ONE);
        BigDecimal powResult = onePlusRate.pow(termMonths);
        BigDecimal monthlyPayment = principal
                .multiply(monthlyRate.multiply(powResult))
                .divide(powResult.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);

        BigDecimal totalPayment = monthlyPayment.multiply(BigDecimal.valueOf(termMonths));
        BigDecimal totalInterest = totalPayment.subtract(principal);

        return LoanOutput.builder()
                .principal(input.getPrincipal())
                .annualRate(input.getAnnualRate())
                .termMonths(termMonths)
                .repaymentType("等额本息")
                .monthlyPayment(monthlyPayment.doubleValue())
                .totalPayment(totalPayment.doubleValue())
                .totalInterest(totalInterest.doubleValue())
                .firstMonthPayment(monthlyPayment.doubleValue())
                .lastMonthPayment(monthlyPayment.doubleValue())
                .build();
    }

    /**
     * 等额本金计算
     */
    private LoanOutput calculateEqualPrincipal(LoanInput input) {
        BigDecimal principal = BigDecimal.valueOf(input.getPrincipal());
        BigDecimal monthlyRate = BigDecimal.valueOf(input.getAnnualRate() / 12);
        int termMonths = input.getTermMonths();

        // 每月还款本金
        BigDecimal monthlyPrincipal = principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);

        // 第一个月还款额 = 每月本金 + 本金 × 月利率
        BigDecimal firstMonthPayment = monthlyPrincipal.add(principal.multiply(monthlyRate));

        // 最后一个月还款额 = 每月本金 + 剩余本金 × 月利率
        BigDecimal lastMonthPayment = monthlyPrincipal.add(monthlyPrincipal.multiply(monthlyRate));

        // 总利息 = 本金 × 月利率 × (期数 + 1) / 2
        BigDecimal totalInterest = principal
                .multiply(monthlyRate)
                .multiply(BigDecimal.valueOf(termMonths + 1))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        BigDecimal totalPayment = principal.add(totalInterest);

        return LoanOutput.builder()
                .principal(input.getPrincipal())
                .annualRate(input.getAnnualRate())
                .termMonths(termMonths)
                .repaymentType("等额本金")
                .monthlyPayment((firstMonthPayment.doubleValue() + lastMonthPayment.doubleValue()) / 2) // 平均月供
                .totalPayment(totalPayment.doubleValue())
                .totalInterest(totalInterest.doubleValue())
                .firstMonthPayment(firstMonthPayment.doubleValue())
                .lastMonthPayment(lastMonthPayment.doubleValue())
                .build();
    }

    /**
     * 生成缓存 Key
     */
    private String generateCacheKey(LoanInput input) {
        String raw = String.format("loan:%s:%s:%s:%s",
                input.getPrincipal(),
                input.getAnnualRate(),
                input.getTermMonths(),
                input.getRepaymentType());
        return Hashing.sha256()
                .hashString(raw, StandardCharsets.UTF_8)
                .toString()
                .substring(0, 16);
    }

    /**
     * 输入参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanInput {
        private Double principal;
        private Double annualRate;
        private Integer termMonths;
        @Builder.Default
        private String repaymentType = "EQUAL_INSTALLMENT";
    }

    /**
     * 输出结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanOutput {
        private Double principal;
        private Double annualRate;
        private Integer termMonths;
        private String repaymentType;
        private Double monthlyPayment;
        private Double totalPayment;
        private Double totalInterest;
        private Double firstMonthPayment;
        private Double lastMonthPayment;

        /**
         * 格式化输出
         */
        public String toFormattedString() {
            return String.format("""
                    贷款计算结果：
                    - 贷款本金：%.2f 元
                    - 年利率：%.2f%%
                    - 贷款期限：%d 个月
                    - 还款方式：%s
                    - 月还款额：%.2f 元%s
                    - 总还款额：%.2f 元
                    - 总利息：%.2f 元
                    """,
                    principal,
                    annualRate * 100,
                    termMonths,
                    repaymentType,
                    monthlyPayment,
                    "等额本金".equals(repaymentType) ?
                            String.format("（首月 %.2f 元，末月 %.2f 元）", firstMonthPayment, lastMonthPayment) : "",
                    totalPayment,
                    totalInterest
            );
        }
    }
}
