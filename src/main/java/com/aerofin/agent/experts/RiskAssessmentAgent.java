package com.aerofin.agent.experts;

import com.aerofin.agent.AgentMessage;
import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent;
import com.aerofin.config.AgentSystemPrompts;
import com.aerofin.memory.LongTermMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Risk Assessment Agent.
 * <p>
 * Uses long-term memory to enrich prompts with historical user context
 * (Layered Memory pattern: Redis-backed long-term store).
 */
@Slf4j
@Component
public class RiskAssessmentAgent extends BaseAgent {

    private final LongTermMemory longTermMemory;

    public RiskAssessmentAgent(ChatClient chatClient,
                               ApplicationEventPublisher eventPublisher,
                               LongTermMemory longTermMemory) {
        super(AgentRole.RISK_ASSESSMENT, chatClient, eventPublisher);
        this.longTermMemory = longTermMemory;
        log.info("[RiskAssessmentAgent] Initialized");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            String response = chatClient.prompt()
                    .system(getSystemPrompt())
                    .user(buildPrompt(message))
                    .call()
                    .content();
            AgentMessage result = message.createResponse(response);
            result.addData("riskLevel", extractRiskLevel(response));
            return result;
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(buildPrompt(message))
                .stream()
                .content();
    }

    @Override
    protected String getSystemPrompt() { return AgentSystemPrompts.RISK_ASSESSMENT_PROMPT; }

    @Override
    protected List<String> getAvailableTools() { return List.of(); }

    private String buildPrompt(AgentMessage message) {
        StringBuilder sb = new StringBuilder("评估请求: ").append(message.getContent()).append("\n\n");
        // Inject recent long-term memory summaries for richer context
        String sessionId = message.getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            String history = longTermMemory.recallRecent(sessionId, 5);
            if (!history.isBlank()) {
                sb.append("--- 历史会话摘要 ---\n").append(history).append("\n--- 摘要结束 ---\n\n");
            }
        }
        return sb.toString();
    }

    private String extractRiskLevel(String response) {
        if (response.contains("GREEN") || response.contains("低风险")) return "GREEN";
        if (response.contains("RED") || response.contains("高风险")) return "RED";
        return "YELLOW";
    }
}
