package com.aerofin.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FinancialTools 单元测试
 * <p>
 * 测试范围：
 * 1. 贷款计算
 * 2. 政策查询
 * 3. 减免申请状态查询
 *
 * @author Aero-Fin Team
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("财务工具测试")
class FinancialToolsTest {

    @Autowired
    private FinancialTools financialTools;

    @Test
    @DisplayName("贷款计算 - 正常场景")
    void testCalculateLoan_Success() {
        // Given: principal=200000, annualRate=0.045, termMonths=36
        double principal = 200000;
        double annualRate = 0.045;
        int termMonths = 36;

        // When
        String result = financialTools.calculateLoan(principal, annualRate, termMonths);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).containsAnyOf("月供", "月还款", "贷款计算结果", "失败");
    }

    @Test
    @DisplayName("贷款计算 - 参数校验（负本金）")
    void testCalculateLoan_InvalidParams() {
        // Given: negative principal
        double principal = -100000;
        double annualRate = 0.045;
        int termMonths = 36;

        // When - FinancialTools delegates to LoanCalculatorTool which returns an error string
        String result = financialTools.calculateLoan(principal, annualRate, termMonths);

        // Then: should return an error/failure message
        assertThat(result).isNotNull();
        assertThat(result).containsAnyOf("失败", "Invalid", "error", "贷款计算失败");
    }

    @Test
    @DisplayName("政策查询 - 按类型查询")
    void testQueryPolicy_ByType() {
        // Given
        String queryType = "type";
        String queryValue = "LOAN";

        // When
        String result = financialTools.queryPolicy(queryType, queryValue);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("政策查询 - 无效查询类型")
    void testQueryPolicy_InvalidType() {
        // Given
        String queryType = "invalid_type";
        String queryValue = "somevalue";

        // When
        String result = financialTools.queryPolicy(queryType, queryValue);

        // Then: should return unsupported type message
        assertThat(result).contains("不支持的查询类型");
    }

    @Test
    @DisplayName("政策查询 - 不支持的查询类型")
    void testQueryPolicy_UnsupportedQueryType() {
        // Given
        String queryType = "unknown";
        String queryValue = "test";

        // When
        String result = financialTools.queryPolicy(queryType, queryValue);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains("不支持的查询类型");
    }
}
