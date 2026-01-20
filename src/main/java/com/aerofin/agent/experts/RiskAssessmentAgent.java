package com.aerofin.agent.experts;

import com.aerofin.agent.AgentMessage;
import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent;
import com.aerofin.config.AgentSystemPrompts;
import com.aerofin.memory.LayeredMemoryManager;
import com.aerofin.state.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 风控评估Agent
 * <p>
 * 专业领域：
 * 1. 用户风险评估
 * 2. 贷款资格审核
 * 3. 额度预估
 * 4. 反欺诈检测
 * <p>
 * 面试亮点：
 * - 风控规则引擎
 * - 用户画像分析
 * - 风险评分模型
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
public class RiskAssessmentAgent extends BaseAgent {

    private final LayeredMemoryManager memoryManager;

    public RiskAssessmentAgent(
            ChatClient chatClient,
            LayeredMemoryManager memoryManager) {
        super(AgentRole.RISK_ASSESSMENT, chatClient);
        this.memoryManager = memoryManager;
        log.info("[风控专家Agent] 已初始化");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            log.info("[风控专家] 处理任务: {}", message.getContent());

            // 1. 获取用户画像
            String userId = message.getData("userId", String.class);
            UserProfile userProfile = null;
            if (userId != null) {
                userProfile = memoryManager.getLongTermMemory(userId);
            }

            // 2. 构建提示词（包含用户画像）
            String prompt = buildPromptWithUserProfile(message, userProfile);

            // 3. 调用ChatClient进行风险评估
            String response = chatClient.prompt()
                    .system(getSystemPrompt())
                    .user(prompt)
                    .call()
                    .content();

            // 4. 返回结果
            AgentMessage result = message.createResponse(response);
            result.addData("riskLevel", extractRiskLevel(response));
            return result;
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        log.info("[风控专家] 处理流式任务: {}", message.getContent());

        // 获取用户画像
        String userId = message.getData("userId", String.class);
        UserProfile userProfile = null;
        if (userId != null) {
            userProfile = memoryManager.getLongTermMemory(userId);
        }

        String prompt = buildPromptWithUserProfile(message, userProfile);

        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(prompt)
                .stream()
                .content();
    }

    @Override
    protected String getSystemPrompt() {
        return AgentSystemPrompts.RISK_ASSESSMENT_PROMPT;
    }

    @Override
    protected List<String> getAvailableTools() {
        return List.of(); // 风控Agent主要基于规则和AI推理，暂不需要外部工具
    }

    /**
     * 构建带用户画像的提示词
     */
    private String buildPromptWithUserProfile(AgentMessage message, UserProfile userProfile) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("评估请求: ").append(message.getContent()).append("\n\n");

        // 添加用户画像
        if (userProfile != null) {
            prompt.append("--- 用户画像 ---\n");
            prompt.append("用户ID: ").append(userProfile.getUserId()).append("\n");
            prompt.append("年龄: ").append(userProfile.getAge()).append("\n");
            prompt.append("职业: ").append(userProfile.getOccupation()).append("\n");
            prompt.append("收入范围: ").append(userProfile.getIncomeRange()).append("\n");
            prompt.append("注册天数: ").append(userProfile.getRegistrationDays()).append("\n");
            prompt.append("会话总数: ").append(userProfile.getInteractionStats().get("sessionCount")).append("\n");
            prompt.append("工具使用次数: ").append(userProfile.getInteractionStats().get("toolUsageCount")).append("\n");

            // 风险画像
            if (userProfile.getRiskProfile() != null) {
                prompt.append("\n风险画像:\n");
                userProfile.getRiskProfile().forEach((key, value) ->
                        prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            }

            prompt.append("--- 画像结束 ---\n\n");
        } else {
            prompt.append("[注意] 未找到用户画像，将基于当前请求进行初步评估。\n\n");
        }

        return prompt.toString();
    }

    /**
     * 从响应中提取风险等级
     */
    private String extractRiskLevel(String response) {
        if (response.contains("GREEN") || response.contains("低风险")) {
            return "GREEN";
        } else if (response.contains("RED") || response.contains("高风险")) {
            return "RED";
        } else {
            return "YELLOW";
        }
    }
}
