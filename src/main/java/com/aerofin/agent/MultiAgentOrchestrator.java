package com.aerofin.agent;

import com.aerofin.agent.experts.CustomerServiceAgent;
import com.aerofin.agent.experts.LoanExpertAgent;
import com.aerofin.agent.experts.PolicyExpertAgent;
import com.aerofin.agent.experts.RiskAssessmentAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 多Agent编排器
 * <p>
 * 核心功能：
 * 1. 管理所有专家Agent的生命周期
 * 2. 协调Agent间的消息传递
 * 3. 处理单Agent和多Agent协作场景
 * 4. 提供统一的对外接口
 * <p>
 * 架构设计：
 * <pre>
 * User Request
 *      ↓
 * MultiAgentOrchestrator
 *      ↓
 * CoordinatorAgent (意图识别、任务路由)
 *      ↓
 * ┌─────────────┬─────────────┬─────────────┬─────────────┐
 * │ LoanExpert  │ PolicyExpert│ RiskExpert  │ CSExpert    │
 * └─────────────┴─────────────┴─────────────┴─────────────┘
 *      ↓              ↓              ↓              ↓
 * Result Aggregation (结果聚合)
 *      ↓
 * Final Response
 * </pre>
 * <p>
 * 面试亮点：
 * - 多Agent协作编排
 * - 任务路由与负载均衡
 * - 结果聚合策略
 * - 性能监控与指标统计
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    // 协调器
    private final CoordinatorAgent coordinatorAgent;

    // 专家Agent
    private final LoanExpertAgent loanExpertAgent;
    private final PolicyExpertAgent policyExpertAgent;
    private final RiskAssessmentAgent riskAssessmentAgent;
    private final CustomerServiceAgent customerServiceAgent;

    /**
     * Agent注册表（用于快速查找）
     */
    private final Map<AgentRole, BaseAgent> agentRegistry = new HashMap<>();

    /**
     * 初始化Agent注册表
     */
    @jakarta.annotation.PostConstruct
    public void initialize() {
        agentRegistry.put(AgentRole.COORDINATOR, coordinatorAgent);
        agentRegistry.put(AgentRole.LOAN_EXPERT, loanExpertAgent);
        agentRegistry.put(AgentRole.POLICY_EXPERT, policyExpertAgent);
        agentRegistry.put(AgentRole.RISK_ASSESSMENT, riskAssessmentAgent);
        agentRegistry.put(AgentRole.CUSTOMER_SERVICE, customerServiceAgent);

        log.info("🚀 MultiAgentOrchestrator 初始化完成，注册了 {} 个Agent", agentRegistry.size());
    }

    /**
     * 处理用户请求（非流式）
     * <p>
     * 执行流程：
     * 1. 协调器识别意图
     * 2. 路由到专家Agent
     * 3. 执行任务
     * 4. 返回结果
     *
     * @param userMessage 用户消息
     * @param sessionId   会话ID
     * @param userId      用户ID
     * @return Agent响应
     */
    public Mono<String> processRequest(String userMessage, String sessionId, String userId) {
        log.info("📥 [Orchestrator] 收到请求: sessionId={}, userId={}, message={}",
                sessionId, userId, userMessage);

        // 1. 创建用户消息
        AgentMessage userMsg = AgentMessage.builder()
                .sender(AgentRole.COORDINATOR)
                .receiver(AgentRole.COORDINATOR)
                .messageType(AgentMessage.MessageType.TASK_ASSIGNMENT)
                .content(userMessage)
                .sessionId(sessionId)
                .build();
        userMsg.addData("userId", userId);

        // 2. 协调器识别意图并路由
        return coordinatorAgent.execute(userMsg)
                .flatMap(routingResult -> {
                    // 获取目标Agent
                    AgentRole targetRole = AgentRole.valueOf(
                            routingResult.getData("targetAgent", String.class)
                    );
                    BaseAgent targetAgent = agentRegistry.get(targetRole);

                    log.info("🎯 [Orchestrator] 路由到: {}", targetRole.getName());

                    // 获取路由消息
                    @SuppressWarnings("unchecked")
                    AgentMessage routingMessage = (AgentMessage) routingResult.getData().get("routingMessage");

                    // 3. 执行专家Agent
                    return targetAgent.execute(routingMessage)
                            .map(AgentMessage::getContent);
                });
    }

    /**
     * 处理用户请求（流式）
     * <p>
     * 流式输出场景，直接路由到专家Agent进行流式处理
     *
     * @param userMessage 用户消息
     * @param sessionId   会话ID
     * @param userId      用户ID
     * @return 流式响应
     */
    public Flux<String> processRequestStream(String userMessage, String sessionId, String userId) {
        log.info("📥 [Orchestrator] 收到流式请求: sessionId={}, userId={}, message={}",
                sessionId, userId, userMessage);

        return Flux.defer(() -> {
            // 1. 意图识别
            AgentRole targetRole = coordinatorAgent.identifyIntent(userMessage);
            BaseAgent targetAgent = agentRegistry.get(targetRole);

            log.info("🎯 [Orchestrator] 流式路由到: {}", targetRole.getName());

            // 2. 创建消息
            AgentMessage message = AgentMessage.createTaskAssignment(
                    AgentRole.COORDINATOR,
                    targetRole,
                    userMessage,
                    sessionId
            );
            message.addData("userId", userId);

            // 3. 流式执行
            return targetAgent.executeStream(message);
        });
    }

    /**
     * 多Agent协作处理（复杂场景）
     * <p>
     * 当一个请求需要多个Agent协作时使用
     * 例如："我想贷款20万，有什么优惠政策吗？"
     * 需要：贷款专家（计算）+ 政策专家（查询政策）
     *
     * @param userMessage 用户消息
     * @param sessionId   会话ID
     * @param userId      用户ID
     * @return 聚合后的响应
     */
    public Mono<String> processMultiAgentRequest(String userMessage, String sessionId, String userId) {
        log.info("🔀 [Orchestrator] 多Agent协作请求: {}", userMessage);

        // 1. 判断需要哪些Agent协作
        return Flux.fromIterable(agentRegistry.values())
                .filter(agent -> agent.getRole() != AgentRole.COORDINATOR)
                .flatMap(agent -> {
                    // 创建消息
                    AgentMessage message = AgentMessage.createTaskAssignment(
                            AgentRole.COORDINATOR,
                            agent.getRole(),
                            userMessage,
                            sessionId
                    );
                    message.addData("userId", userId);

                    // 并行执行
                    return agent.execute(message)
                            .map(result -> Map.entry(agent.getRole(), result.getContent()))
                            .onErrorResume(error -> {
                                log.warn("[Orchestrator] Agent {} 执行失败: {}",
                                        agent.getRole().getName(), error.getMessage());
                                return Mono.empty();
                            });
                })
                .collectList()
                .map(results -> {
                    // 2. 聚合结果
                    StringBuilder aggregated = new StringBuilder();
                    aggregated.append("以下是多个专家Agent的分析结果：\n\n");

                    results.forEach(entry -> {
                        aggregated.append("【").append(entry.getKey().getName()).append("】\n");
                        aggregated.append(entry.getValue()).append("\n\n");
                    });

                    return aggregated.toString();
                });
    }

    /**
     * 获取Agent状态摘要
     */
    public Map<String, Object> getAgentStatusSummary() {
        Map<String, Object> summary = new HashMap<>();

        agentRegistry.forEach((role, agent) -> {
            Map<String, Object> agentStatus = new HashMap<>();
            agentStatus.put("state", agent.getState().name());
            agentStatus.put("totalProcessed", agent.getMetrics().get("totalProcessed"));
            agentStatus.put("totalErrors", agent.getMetrics().get("totalErrors"));
            agentStatus.put("avgResponseTime", agent.getAverageResponseTime());

            summary.put(role.getName(), agentStatus);
        });

        return summary;
    }

    /**
     * 获取指定角色的Agent
     */
    public BaseAgent getAgent(AgentRole role) {
        return agentRegistry.get(role);
    }

    /**
     * 重置所有Agent的指标
     */
    public void resetAllMetrics() {
        agentRegistry.values().forEach(BaseAgent::resetMetrics);
        log.info("🔄 所有Agent指标已重置");
    }
}
