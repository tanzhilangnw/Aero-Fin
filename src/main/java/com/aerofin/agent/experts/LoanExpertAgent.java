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
 * 贷款专家Agent
 * <p>
 * 专业领域：
 * 1. 贷款计算（月供、总利息、总还款额）
 * 2. 还款方式咨询（等额本息 vs 等额本金）
 * 3. 提前还款计算
 * 4. 贷款产品推荐
 * <p>
 * 面试亮点：
 * - 领域专家Agent实现
 * - 工具调用封装（calculateLoan）
 * - 专业化系统提示词
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
public class LoanExpertAgent extends BaseAgent {

    private final FinancialTools financialTools;

    public LoanExpertAgent(ChatClient chatClient, FinancialTools financialTools) {
        super(AgentRole.LOAN_EXPERT, chatClient);
        this.financialTools = financialTools;
        log.info("[贷款专家Agent] 已初始化");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            log.info("[贷款专家] 处理任务: {}", message.getContent());

            // 调用ChatClient进行推理
            String prompt = buildPrompt(message);
            String response = chatClient.prompt()
                    .system(getSystemPrompt())
                    .user(prompt)
                    .functions("calculateLoan") // 注册工具
                    .call()
                    .content();

            // 返回结果
            return message.createResponse(response);
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        log.info("[贷款专家] 处理流式任务: {}", message.getContent());

        String prompt = buildPrompt(message);

        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(prompt)
                .functions("calculateLoan")
                .stream()
                .content();
    }

    @Override
    protected String getSystemPrompt() {
        return AgentSystemPrompts.CALCULATOR_PROMPT;
    }

    @Override
    protected List<String> getAvailableTools() {
        return List.of("calculateLoan");
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(AgentMessage message) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户问题: ").append(message.getContent()).append("\n\n");

        // 添加上下文数据（如果有）
        if (message.getData() != null && !message.getData().isEmpty()) {
            prompt.append("补充信息:\n");
            message.getData().forEach((key, value) ->
                    prompt.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        return prompt.toString();
    }
}
