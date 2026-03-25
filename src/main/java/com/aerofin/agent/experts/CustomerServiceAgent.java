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
public class CustomerServiceAgent extends BaseAgent {

    private final FinancialTools financialTools;

    public CustomerServiceAgent(ChatClient chatClient,
                                ApplicationEventPublisher eventPublisher,
                                FinancialTools financialTools) {
        super(AgentRole.CUSTOMER_SERVICE, chatClient, eventPublisher);
        this.financialTools = financialTools;
        log.info("[CustomerServiceAgent] Initialized");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            String response = chatClient.prompt()
                    .system(getSystemPrompt())
                    .user(buildPrompt(message))
                    .functions("applyWaiver", "queryWaiverStatus")
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
                .functions("applyWaiver", "queryWaiverStatus")
                .stream()
                .content();
    }

    @Override
    protected String getSystemPrompt() { return AgentSystemPrompts.ACTION_SOP_PROMPT; }

    @Override
    protected List<String> getAvailableTools() { return List.of("applyWaiver", "queryWaiverStatus"); }

    private String buildPrompt(AgentMessage message) {
        StringBuilder sb = new StringBuilder("客户请求: ").append(message.getContent()).append("\n\n");
        if (message.getData() != null && !message.getData().isEmpty()) {
            sb.append("补充信息:\n");
            message.getData().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        return sb.toString();
    }
}
