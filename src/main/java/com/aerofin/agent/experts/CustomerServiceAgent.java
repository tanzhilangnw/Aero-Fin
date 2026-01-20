package com.aerofin.agent.experts;

import com.aerofin.agent.AgentMessage;
import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent;
import com.aerofin.config.AgentSystemPrompts;
import com.aerofin.tools.FinancialTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 客服专家Agent
 * <p>
 * 专业领域：
 * 1. 投诉处理
 * 2. 罚息减免申请
 * 3. 账户查询
 * 4. 常见问题解答
 * <p>
 * 面试亮点：
 * - 客户服务流程自动化
 * - 情感分析与安抚
 * - 工单系统集成
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
public class CustomerServiceAgent extends BaseAgent {

    private final FinancialTools financialTools;

    public CustomerServiceAgent(
            ChatClient chatClient,
            FinancialTools financialTools) {
        super(AgentRole.CUSTOMER_SERVICE, chatClient);
        this.financialTools = financialTools;
        log.info("[客服专家Agent] 已初始化");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            log.info("[客服专家] 处理任务: {}", message.getContent());

            String prompt = buildPrompt(message);

            String response = chatClient.prompt()
                    .system(getSystemPrompt())
                    .user(prompt)
                    .functions("applyWaiver", "queryWaiverStatus") // 注册客服工具
                    .call()
                    .content();

            return message.createResponse(response);
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        log.info("[客服专家] 处理流式任务: {}", message.getContent());

        String prompt = buildPrompt(message);

        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(prompt)
                .functions("applyWaiver", "queryWaiverStatus")
                .stream()
                .content();
    }

    @Override
    protected String getSystemPrompt() {
        return AgentSystemPrompts.ACTION_SOP_PROMPT;
    }

    @Override
    protected List<String> getAvailableTools() {
        return List.of("applyWaiver", "queryWaiverStatus");
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(AgentMessage message) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("客户请求: ").append(message.getContent()).append("\n\n");

        if (message.getData() != null && !message.getData().isEmpty()) {
            prompt.append("补充信息:\n");
            message.getData().forEach((key, value) ->
                    prompt.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        return prompt.toString();
    }
}
