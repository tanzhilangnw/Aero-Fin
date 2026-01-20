package com.aerofin.agent;

import com.aerofin.config.AgentSystemPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 协调器Agent
 * <p>
 * 核心职责：
 * 1. 意图识别 - 分析用户请求，识别意图类型
 * 2. 任务路由 - 将任务分发给最合适的专家Agent
 * 3. 结果聚合 - 整合多个Agent的返回结果
 * 4. 复杂编排 - 协调多步骤任务的执行
 * <p>
 * 面试亮点：
 * - 多Agent协作编排
 * - 意图识别与任务路由
 * - 结果聚合策略
 * - 复杂工作流管理
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
public class CoordinatorAgent extends BaseAgent {

    public CoordinatorAgent(ChatClient chatClient) {
        super(AgentRole.COORDINATOR, chatClient);
        log.info("[协调器Agent] 已初始化");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            log.info("[协调器] 分析任务: {}", message.getContent());

            // 1. 判断是否需要多Agent协作
            boolean needMultiAgent = requiresMultiAgent(message.getContent());
            log.info("[协调器] 多Agent协作判断: {}", needMultiAgent);

            if (needMultiAgent) {
                // 多Agent协作场景
                log.info("[协调器] 检测到多Agent协作需求，识别所需Agents...");
                List<AgentRole> requiredAgents = identifyRequiredAgents(message.getContent());
                log.info("[协调器] 需要协作的Agents: {}", requiredAgents);

                // 返回多Agent协作决策
                AgentMessage result = message.createResponse("任务需要多Agent协作");
                result.addData("requiresMultiAgent", true);
                result.addData("requiredAgents", requiredAgents);
                result.addData("originalMessage", message.getContent());
                return result;
            } else {
                // 单Agent场景
                AgentRole targetRole = identifyIntent(message.getContent());
                log.info("[协调器] 识别意图 -> 路由到: {}", targetRole.getName());

                // 创建路由消息
                AgentMessage routingMessage = AgentMessage.createTaskAssignment(
                        AgentRole.COORDINATOR,
                        targetRole,
                        message.getContent(),
                        message.getSessionId()
                );

                // 将原始消息的数据传递给目标Agent
                if (message.getData() != null) {
                    routingMessage.getData().putAll(message.getData());
                }

                // 返回单Agent路由决策
                AgentMessage result = message.createResponse(
                        String.format("任务已路由到 [%s]", targetRole.getName())
                );
                result.addData("requiresMultiAgent", false);
                result.addData("targetAgent", targetRole.name());
                result.addData("routingMessage", routingMessage);

                return result;
            }
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        log.info("[协调器] 流式处理任务: {}", message.getContent());

        // 协调器通常不做流式输出，而是直接路由
        return Flux.just("正在分析您的请求...\n")
                .concatWith(Flux.defer(() -> {
                    AgentRole targetRole = identifyIntent(message.getContent());
                    return Flux.just(String.format("已路由到 [%s]\n", targetRole.getName()));
                }));
    }

    @Override
    protected String getSystemPrompt() {
        return AgentSystemPrompts.SUPERVISOR_PROMPT;
    }

    @Override
    protected List<String> getAvailableTools() {
        return List.of(); // 协调器不直接调用工具，而是委派给专家Agent
    }

    /**
     * 意图识别 - 根据用户消息内容推断最合适的Agent（单Agent场景）
     * <p>
     * 使用规则+AI混合方式：
     * 1. 优先使用规则匹配（快速、可控）
     * 2. 规则无法判断时，使用AI进行意图识别
     */
    public AgentRole identifyIntent(String userMessage) {
        // 复用统一的意图识别逻辑
        List<AgentRole> allIntents = identifyAllIntents(userMessage);

        // 返回优先级最高的Agent
        if (!allIntents.isEmpty()) {
            AgentRole primaryAgent = allIntents.get(0);
            log.debug("[协调器] 单Agent识别成功: {} -> {}", userMessage, primaryAgent.getName());
            return primaryAgent;
        }

        // 如果没有匹配到任何Agent，默认使用贷款专家
        log.debug("[协调器] 未识别到明确意图，默认使用贷款专家");
        return AgentRole.LOAN_EXPERT;
    }

    /**
     * 统一的意图识别方法 - 识别用户消息涉及的所有领域
     * <p>
     * 使用规则+AI混合方式：
     * 1. 优先使用规则匹配（快速、可控）
     * 2. 规则匹配失败或不确定时，使用AI增强识别
     * <p>
     * 返回按优先级排序的Agent列表
     *
     * @param userMessage 用户消息
     * @return 涉及的Agent列表（按优先级排序）
     */
    private List<AgentRole> identifyAllIntents(String userMessage) {
        List<AgentRole> agents = new java.util.ArrayList<>();

        // 1. 规则匹配（快速路径）
        if (userMessage.matches(".*(贷款|月供|利率|计算|还款|本金|利息).*")) {
            agents.add(AgentRole.LOAN_EXPERT);
        }
        if (userMessage.matches(".*(政策|优惠|条件|活动|要求|规定).*")) {
            agents.add(AgentRole.POLICY_EXPERT);
        }
        if (userMessage.matches(".*(额度|审批|征信|资格|能贷|评估).*")) {
            agents.add(AgentRole.RISK_ASSESSMENT);
        }
        if (userMessage.matches(".*(投诉|减免|罚息|申请|办理|修改).*")) {
            agents.add(AgentRole.CUSTOMER_SERVICE);
        }

        // 2. 如果规则匹配成功，直接返回
        if (!agents.isEmpty()) {
            log.debug("[协调器] 规则匹配成功: {} -> {}", userMessage, agents);
            return agents;
        }

        // 3. 规则匹配失败，使用AI意图识别（复杂场景）
        log.debug("[协调器] 规则匹配失败，使用AI进行意图识别: {}", userMessage);

        try {
            String intentPrompt = String.format("""
                    分析以下用户请求，判断需要哪些专家Agent参与处理（可能需要1个或多个）。

                    用户请求: %s

                    可选Agent:
                    1. LOAN_EXPERT - 贷款计算、还款咨询、月供计算
                    2. POLICY_EXPERT - 政策查询、优惠活动、贷款条件
                    3. RISK_ASSESSMENT - 风险评估、资格审核、额度评估
                    4. CUSTOMER_SERVICE - 投诉处理、罚息减免、业务办理

                    请返回需要的Agent名称，多个用逗号分隔，按优先级排序。
                    例如: LOAN_EXPERT,POLICY_EXPERT
                    如果只需要一个，返回: LOAN_EXPERT
                    """, userMessage);

            String response = chatClient.prompt()
                    .user(intentPrompt)
                    .call()
                    .content()
                    .trim()
                    .toUpperCase();

            // 解析AI返回的Agent名称列表
            String[] agentNames = response.split("[,，\\s]+");
            for (String agentName : agentNames) {
                try {
                    AgentRole role = AgentRole.valueOf(agentName.trim());
                    if (role != AgentRole.COORDINATOR && !agents.contains(role)) {
                        agents.add(role);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("[协调器] AI返回的Agent名称无法识别: {}", agentName);
                }
            }

            log.info("[协调器] AI意图识别结果: {}", agents);

        } catch (Exception e) {
            log.error("[协调器] AI意图识别失败", e);
        }

        // 4. 如果AI也没有返回结果，兜底使用贷款专家
        if (agents.isEmpty()) {
            log.warn("[协调器] 意图识别失败，使用默认Agent: LOAN_EXPERT");
            agents.add(AgentRole.LOAN_EXPERT);
        }

        return agents;
    }

    /**
     * 判断是否需要多Agent协作
     */
    public boolean requiresMultiAgent(String userMessage) {
        // 简单规则：如果消息同时包含多个领域关键词，则需要多Agent协作
        int domainCount = 0;

        if (userMessage.matches(".*(贷款|月供|利率|计算|还款).*")) domainCount++;
        if (userMessage.matches(".*(政策|优惠|条件|活动).*")) domainCount++;
        if (userMessage.matches(".*(额度|审批|征信|资格|能贷).*")) domainCount++;
        if (userMessage.matches(".*(投诉|减免|罚息|申请).*")) domainCount++;

        return domainCount >= 2;
    }

    /**
     * 识别需要协作的Agents列表（多Agent场景）
     * <p>
     * 复用统一的意图识别逻辑 {@link #identifyAllIntents(String)}
     * 支持规则匹配 + AI增强识别
     *
     * @param userMessage 用户消息
     * @return 需要的Agent角色列表（按优先级排序）
     */
    public List<AgentRole> identifyRequiredAgents(String userMessage) {
        // 复用统一的意图识别逻辑
        List<AgentRole> agents = identifyAllIntents(userMessage);

        log.info("[协调器] 多Agent场景识别结果: {} -> {}", userMessage, agents);

        return agents;
    }
}
