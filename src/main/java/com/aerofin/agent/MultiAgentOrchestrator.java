package com.aerofin.agent;

import com.aerofin.agent.experts.CustomerServiceAgent;
import com.aerofin.agent.experts.LoanExpertAgent;
import com.aerofin.agent.experts.PolicyExpertAgent;
import com.aerofin.agent.experts.RiskAssessmentAgent;
import com.aerofin.agent.experts.ReflectAgent;
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
    private final ReflectAgent reflectAgent;

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
        agentRegistry.put(AgentRole.REFLECTOR, reflectAgent);

        log.info("🚀 MultiAgentOrchestrator 初始化完成，注册了 {} 个Agent", agentRegistry.size());
    }

    /**
     * 处理用户请求（非流式）
     * <p>
     * 执行流程：
     * 1. 协调器识别意图
     * 2. 判断是单Agent还是多Agent协作
     * 3. 路由到专家Agent（单个或多个）
     * 4. 执行任务
     * 5. 聚合并返回结果
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
                    // 判断是否需要多Agent协作
                    Boolean requiresMultiAgent = routingResult.getData("requiresMultiAgent", Boolean.class);

                    if (Boolean.TRUE.equals(requiresMultiAgent)) {
                        // 多Agent协作场景
                        log.info("🔀 [Orchestrator] 多Agent协作模式");
                        @SuppressWarnings("unchecked")
                        List<AgentRole> requiredAgents = (List<AgentRole>) routingResult.getData().get("requiredAgents");

                        return processMultiAgentInternal(userMessage, sessionId, userId, requiredAgents);
                    } else {
                        // 单Agent场景
                        AgentRole targetRole = AgentRole.valueOf(
                                routingResult.getData("targetAgent", String.class)
                        );
                        BaseAgent targetAgent = agentRegistry.get(targetRole);

                        log.info("🎯 [Orchestrator] 单Agent路由到: {}", targetRole.getName());

                        // 获取路由消息
                        @SuppressWarnings("unchecked")
                        AgentMessage routingMessage = (AgentMessage) routingResult.getData().get("routingMessage");

                        // 执行专家Agent
                        return targetAgent.execute(routingMessage)
                                .map(AgentMessage::getContent);
                    }
                });
    }

    /**
     * 处理用户请求并经过 ReflectAgent 二次审阅（非流式）
     * <p>
     * 执行流程：
     * 1. 正常通过 Coordinator 路由到目标专家 Agent，生成初稿回答
     * 2. 将 userMessage + draftAnswer 封装为 AgentMessage 发给 ReflectAgent
     * 3. 返回 ReflectAgent 的审阅结果（其中包含修订版回答）
     */
    public Mono<String> processRequestWithReflection(String userMessage, String sessionId, String userId) {
        return processRequest(userMessage, sessionId, userId)
                .flatMap(draftAnswer -> {
                    // 构造发给 ReflectAgent 的消息
                    AgentMessage reflectMsg = AgentMessage.createTaskAssignment(
                            AgentRole.COORDINATOR,
                            AgentRole.REFLECTOR,
                            "请审阅以下回答的合规性与风险提示是否充分。",
                            sessionId
                    );
                    reflectMsg.addData("userId", userId);
                    reflectMsg.addData("userQuestion", userMessage);
                    reflectMsg.addData("draftAnswer", draftAnswer);
                    reflectMsg.addData("sourceAgent", "AUTO"); // 简化：暂不传具体来源

                    return reflectAgent.execute(reflectMsg)
                            .map(AgentMessage::getContent);
                });
    }

    /**
     * 处理用户请求（流式）
     * <p>
     * 流式输出场景，支持单Agent和多Agent协作
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
            // 1. 判断是否需要多Agent协作
            boolean needMultiAgent = coordinatorAgent.requiresMultiAgent(userMessage);

            if (needMultiAgent) {
                // 多Agent协作场景（流式）
                log.info("🔀 [Orchestrator] 流式多Agent协作模式");
                List<AgentRole> requiredAgents = coordinatorAgent.identifyRequiredAgents(userMessage);

                return Flux.just("正在协调多个专家Agent为您服务...\n\n")
                        .concatWith(processMultiAgentInternal(userMessage, sessionId, userId, requiredAgents)
                                .flatMapMany(Flux::just));
            } else {
                // 单Agent场景
                AgentRole targetRole = coordinatorAgent.identifyIntent(userMessage);
                BaseAgent targetAgent = agentRegistry.get(targetRole);

                log.info("🎯 [Orchestrator] 流式路由到: {}", targetRole.getName());

                // 创建消息
                AgentMessage message = AgentMessage.createTaskAssignment(
                        AgentRole.COORDINATOR,
                        targetRole,
                        userMessage,
                        sessionId
                );
                message.addData("userId", userId);

                // 流式执行
                return targetAgent.executeStream(message);
            }
        });
    }

    /**
     * 多Agent协作处理（复杂场景）- 公开API
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

        // 使用协调器识别需要的Agents
        List<AgentRole> requiredAgents = coordinatorAgent.identifyRequiredAgents(userMessage);
        return processMultiAgentInternal(userMessage, sessionId, userId, requiredAgents);
    }

    /**
     * 多Agent协作处理 - 内部实现
     * <p>
     * 根据指定的Agent列表并行执行，并聚合结果
     *
     * @param userMessage    用户消息
     * @param sessionId      会话ID
     * @param userId         用户ID
     * @param requiredAgents 需要协作的Agent列表
     * @return 聚合后的响应
     */
    private Mono<String> processMultiAgentInternal(String userMessage, String sessionId,
                                                     String userId, List<AgentRole> requiredAgents) {
        log.info("🔀 [Orchestrator] 执行多Agent协作，涉及Agents: {}", requiredAgents);

        // 并行执行所有需要的Agent
        return Flux.fromIterable(requiredAgents)
                .flatMap(role -> {
                    BaseAgent agent = agentRegistry.get(role);
                    if (agent == null) {
                        log.warn("[Orchestrator] Agent {} 未注册", role.getName());
                        return Mono.empty();
                    }

                    // 创建消息
                    AgentMessage message = AgentMessage.createTaskAssignment(
                            AgentRole.COORDINATOR,
                            role,
                            userMessage,
                            sessionId
                    );
                    message.addData("userId", userId);

                    // 执行Agent
                    return agent.execute(message)
                            .map(result -> Map.entry(role, result.getContent()))
                            .onErrorResume(error -> {
                                log.warn("[Orchestrator] Agent {} 执行失败: {}",
                                        role.getName(), error.getMessage());
                                return Mono.just(Map.entry(role,
                                        String.format("【%s处理失败：%s】", role.getName(), error.getMessage())));
                            });
                })
                .collectList()
                .map(results -> {
                    // 聚合结果
                    StringBuilder aggregated = new StringBuilder();
                    aggregated.append("📋 综合多位专家的分析结果：\n\n");

                    results.forEach(entry -> {
                        aggregated.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
                        aggregated.append("【").append(entry.getKey().getName()).append("】\n\n");
                        aggregated.append(entry.getValue()).append("\n\n");
                    });

                    aggregated.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    aggregated.append("以上是 ").append(results.size()).append(" 位专家的综合意见。");

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
