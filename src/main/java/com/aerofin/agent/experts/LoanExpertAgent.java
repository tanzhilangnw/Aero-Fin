package com.aerofin.agent.experts;

import com.aerofin.agent.AgentMessage;
import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent;
import com.aerofin.config.AgentSystemPrompts;
import com.aerofin.tools.FinancialTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class LoanExpertAgent extends BaseAgent {

    private final FinancialTools financialTools;

    public LoanExpertAgent(ChatClient chatClient,
                           ApplicationEventPublisher eventPublisher,
                           FinancialTools financialTools) {
        super(AgentRole.LOAN_EXPERT, chatClient, eventPublisher);
        this.financialTools = financialTools;
        log.info("[LoanExpertAgent] Initialized");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            String response = chatClient.prompt()
                    .system(getSystemPrompt())
                    .user(buildPrompt(message))
                    .functions("calculateLoan")
                    .call()
                    .content();
            return message.createResponse(response);
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(buildPrompt(message))
                .functions("calculateLoan")
                .stream()
                .content();
    }

    @Override
    protected String getSystemPrompt() { return AgentSystemPrompts.CALCULATOR_PROMPT; }

    @Override
    protected List<String> getAvailableTools() { return List.of("calculateLoan"); }

    private String buildPrompt(AgentMessage message) {
        StringBuilder sb = new StringBuilder("用户问题: ").append(message.getContent()).append("\n\n");
        if (message.getData() != null && !message.getData().isEmpty()) {
            sb.append("补充信息:\n");
            message.getData().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        return sb.toString();
    }
}
