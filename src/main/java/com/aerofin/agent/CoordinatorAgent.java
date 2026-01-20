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

            // 1. 意图识别
            AgentRole targetRole = identifyIntent(message.getContent());
            log.info("[协调器] 识别意图 -> 路由到: {}", targetRole.getName());

            // 2. 创建路由消息
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

            // 3. 返回路由决策
            AgentMessage result = message.createResponse(
                    String.format("任务已路由到 [%s]", targetRole.getName())
            );
            result.addData("targetAgent", targetRole.name());
            result.addData("routingMessage", routingMessage);

            return result;
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
     * 意图识别 - 根据用户消息内容推断最合适的Agent
     * <p>
     * 使用规则+AI混合方式：
     * 1. 优先使用规则匹配（快速、可控）
     * 2. 规则无法判断时，使用AI进行意图识别
     */
    public AgentRole identifyIntent(String userMessage) {
        // 1. 规则匹配（快速路径）
        AgentRole ruleBasedRole = AgentRole.inferFromIntent(userMessage);

        // 如果规则匹配返回的不是COORDINATOR，说明匹配成功
        if (ruleBasedRole != AgentRole.COORDINATOR) {
            log.debug("[协调器] 规则匹配成功: {} -> {}", userMessage, ruleBasedRole.getName());
            return ruleBasedRole;
        }

        // 2. AI意图识别（复杂场景）
        log.debug("[协调器] 使用AI进行意图识别: {}", userMessage);

        try {
            String intentPrompt = String.format("""
                    分析以下用户请求，判断应该路由到哪个专家Agent。

                    用户请求: %s

                    可选Agent:
                    1. LOAN_EXPERT - 贷款计算、还款咨询
                    2. POLICY_EXPERT - 政策查询、优惠活动
                    3. RISK_ASSESSMENT - 风险评估、资格审核
                    4. CUSTOMER_SERVICE - 投诉处理、罚息减免

                    只需返回Agent名称（例如: LOAN_EXPERT），不要返回其他内容。
                    """, userMessage);

            String response = chatClient.prompt()
                    .user(intentPrompt)
                    .call()
                    .content()
                    .trim()
                    .toUpperCase();

            // 解析AI返回的Agent名称
            try {
                return AgentRole.valueOf(response);
            } catch (IllegalArgumentException e) {
                log.warn("[协调器] AI返回的Agent名称无法识别: {}, 默认使用贷款专家", response);
                return AgentRole.LOAN_EXPERT;
            }

        } catch (Exception e) {
            log.error("[协调器] 意图识别失败", e);
            // 默认路由到贷款专家
            return AgentRole.LOAN_EXPERT;
        }
    }

    /**
     * 判断是否需要多Agent协作
     */
    public boolean requiresMultiAgent(String userMessage) {
        // 简单规则：如果消息同时包含多个领域关键词，则需要多Agent协作
        int domainCount = 0;

        if (userMessage.matches(".*(贷款|月供|利率).*")) domainCount++;
        if (userMessage.matches(".*(政策|优惠|条件).*")) domainCount++;
        if (userMessage.matches(".*(额度|审批|征信).*")) domainCount++;
        if (userMessage.matches(".*(投诉|减免|罚息).*")) domainCount++;

        return domainCount >= 2;
    }
}
