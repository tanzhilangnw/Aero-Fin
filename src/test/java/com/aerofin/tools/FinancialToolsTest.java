package com.aerofin.tools;

import com.aerofin.model.vo.ToolCallResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FinancialTools 单元测试
 * <p>
 * 测试范围：
 * 1. 贷款计算
 * 2. 政策查询
 * 3. 减免申请
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
        // Given
        double principal = 200000;  // 20万
        int months = 36;            // 3年
        double rate = 0.045;        // 4.5%

        // When
        ToolCallResult result = financialTools.calculateLoan(principal, months, rate);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
        // 月供应该在 5500-6000 之间
        String data = result.getData().toString();
        assertThat(data).contains("月供");
    }

    @Test
    @DisplayName("贷款计算 - 参数校验")
    void testCalculateLoan_InvalidParams() {
        // Given
        double principal = -100000;  // 负数
        int months = 36;
        double rate = 0.045;

        // When & Then
        assertThatThrownBy(() -> financialTools.calculateLoan(principal, months, rate))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("政策查询 - 成功")
    void testQueryPolicy_Success() {
        // Given
        String policyType = "small_business_loan";

        // When
        ToolCallResult result = financialTools.queryPolicy(policyType);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("政策查询 - 无效政策类型")
    void testQueryPolicy_InvalidType() {
        // Given
        String policyType = "non_existent_policy";

        // When
        ToolCallResult result = financialTools.queryPolicy(policyType);

        // Then
        assertThat(result.getSuccess()).isFalse();
    }

    @Test
    @DisplayName("减免申请 - 成功")
    void testApplyWaiver_Success() {
        // Given
        String userId = "user-123";
        String reason = "因疫情影响，生意不好做";
        double waiverAmount = 500;

        // When
        ToolCallResult result = financialTools.applyWaiver(userId, reason, waiverAmount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
    }

    @Test
    @DisplayName("减免申请 - 金额过大")
    void testApplyWaiver_ExceedsLimit() {
        // Given
        String userId = "user-123";
        String reason = "test";
        double waiverAmount = 10000;  // 超过限额

        // When
        ToolCallResult result = financialTools.applyWaiver(userId, reason, waiverAmount);

        // Then
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).contains("超出");
    }
}
