package com.aerofin.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CoordinatorAgent 单元测试
 * <p>
 * 测试范围：
 * 1. 意图识别（单Agent）
 * 2. 多Agent协作判断
 * 3. 多Agent识别
 *
 * @author Aero-Fin Team
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("协调器Agent测试")
class CoordinatorAgentTest {

    @Autowired
    private CoordinatorAgent coordinatorAgent;

    @Nested
    @DisplayName("单Agent意图识别测试")
    class IdentifyIntentTests {

        @Test
        @DisplayName("识别贷款计算意图")
        void testIdentifyIntent_LoanCalculation() {
            // Given
            String userMessage = "贷款20万，3年，利率4.5%，每月还多少？";

            // When
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);

            // Then
            assertThat(role).isEqualTo(AgentRole.LOAN_EXPERT);
        }

        @Test
        @DisplayName("识别政策查询意图")
        void testIdentifyIntent_PolicyQuery() {
            // Given
            String userMessage = "小微企业贷款有什么优惠政策？";

            // When
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);

            // Then
            assertThat(role).isEqualTo(AgentRole.POLICY_EXPERT);
        }

        @Test
        @DisplayName("识别风控评估意图")
        void testIdentifyIntent_RiskAssessment() {
            // Given
            String userMessage = "我能贷多少额度？";

            // When
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);

            // Then
            assertThat(role).isEqualTo(AgentRole.RISK_ASSESSMENT);
        }

        @Test
        @DisplayName("识别客服办理意图")
        void testIdentifyIntent_CustomerService() {
            // Given
            String userMessage = "我想申请减免500元罚息";

            // When
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);

            // Then
            assertThat(role).isEqualTo(AgentRole.CUSTOMER_SERVICE);
        }

        @Test
        @DisplayName("默认兜底到贷款专家")
        void testIdentifyIntent_DefaultToLoanExpert() {
            // Given
            String userMessage = "你好";

            // When
            AgentRole role = coordinatorAgent.identifyIntent(userMessage);

            // Then
            assertThat(role).isEqualTo(AgentRole.LOAN_EXPERT);
        }
    }

    @Nested
    @DisplayName("多Agent协作判断测试")
    class RequiresMultiAgentTests {

        @Test
        @DisplayName("单一领域不触发多Agent")
        void testRequiresMultiAgent_SingleDomain_ReturnsFalse() {
            // Given
            String userMessage = "贷款20万，每月还多少？";

            // When
            boolean result = coordinatorAgent.requiresMultiAgent(userMessage);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("两个领域触发多Agent")
        void testRequiresMultiAgent_TwoDomains_ReturnsTrue() {
            // Given
            String userMessage = "我想贷款20万，有什么优惠政策吗？";

            // When
            boolean result = coordinatorAgent.requiresMultiAgent(userMessage);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("三个领域触发多Agent")
        void testRequiresMultiAgent_ThreeDomains_ReturnsTrue() {
            // Given
            String userMessage = "我能贷多少额度？如果贷50万月供多少？有优惠吗？";

            // When
            boolean result = coordinatorAgent.requiresMultiAgent(userMessage);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("多Agent识别测试")
    class IdentifyRequiredAgentsTests {

        @Test
        @DisplayName("识别贷款+政策两个Agent")
        void testIdentifyRequiredAgents_LoanAndPolicy() {
            // Given
            String userMessage = "我想贷款20万，有什么优惠政策吗？";

            // When
            List<AgentRole> agents = coordinatorAgent.identifyRequiredAgents(userMessage);

            // Then
            assertThat(agents)
                    .hasSize(2)
                    .contains(AgentRole.LOAN_EXPERT, AgentRole.POLICY_EXPERT);
        }

        @Test
        @DisplayName("识别风控+贷款+政策三个Agent")
        void testIdentifyRequiredAgents_RiskLoanPolicy() {
            // Given
            String userMessage = "我能贷多少额度？如果贷50万，每月还多少？有优惠政策吗？";

            // When
            List<AgentRole> agents = coordinatorAgent.identifyRequiredAgents(userMessage);

            // Then
            assertThat(agents)
                    .hasSize(3)
                    .contains(
                            AgentRole.RISK_ASSESSMENT,
                            AgentRole.LOAN_EXPERT,
                            AgentRole.POLICY_EXPERT
                    );
        }

        @Test
        @DisplayName("单一领域只返回一个Agent")
        void testIdentifyRequiredAgents_SingleAgent() {
            // Given
            String userMessage = "贷款20万，每月还多少？";

            // When
            List<AgentRole> agents = coordinatorAgent.identifyRequiredAgents(userMessage);

            // Then
            assertThat(agents)
                    .hasSize(1)
                    .contains(AgentRole.LOAN_EXPERT);
        }
    }
}
