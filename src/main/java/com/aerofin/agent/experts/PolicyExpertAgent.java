package com.aerofin.agent.experts;

import com.aerofin.agent.AgentMessage;
import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent;
import com.aerofin.config.AgentSystemPrompts;
import com.aerofin.service.VectorSearchService;
import com.aerofin.tools.FinancialTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class PolicyExpertAgent extends BaseAgent {

    private final FinancialTools financialTools;
    private final VectorSearchService vectorSearchService;

    public PolicyExpertAgent(ChatClient chatClient,
                             ApplicationEventPublisher eventPublisher,
                             FinancialTools financialTools,
                             VectorSearchService vectorSearchService) {
        super(AgentRole.POLICY_EXPERT, chatClient, eventPublisher);
        this.financialTools = financialTools;
        this.vectorSearchService = vectorSearchService;
        log.info("[PolicyExpertAgent] Initialized");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            List<Document> docs = vectorSearchService.searchRelevantPolicies(message.getContent());
            if (docs.isEmpty()) {
                AgentMessage failure = message.createResponse("RAG search returned no results.");
                failure.setMessageType(AgentMessage.MessageType.TASK_FAILED);
                failure.addData("originalQuery", message.getContent());
                failure.addData("failedAgent", getRole().name());
                failure.addData("failureReason", "RAG_SEARCH_EMPTY");
                return failure;
            }
            String response = chatClient.prompt()
                    .system(getSystemPrompt())
                    .user(buildPromptWithContext(message, docs))
                    .functions("queryPolicy")
                    .call()
                    .content();
            AgentMessage result = message.createResponse(response);
            result.addData("retrievedPolicies", docs.size());
            return result;
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        List<Document> docs = vectorSearchService.searchRelevantPolicies(message.getContent());
        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(buildPromptWithContext(message, docs))
                .functions("queryPolicy")
                .stream()
                .content();
    }

    public List<Document> searchPolicies(String query) {
        return vectorSearchService.searchRelevantPolicies(query);
    }

    @Override
    protected String getSystemPrompt() { return AgentSystemPrompts.POLICY_RAG_PROMPT; }

    @Override
    protected List<String> getAvailableTools() { return List.of("queryPolicy"); }

    private String buildPromptWithContext(AgentMessage message, List<Document> docs) {
        StringBuilder sb = new StringBuilder("用户问题: ").append(message.getContent()).append("\n\n");
        if (!docs.isEmpty()) {
            sb.append("--- 检索到的相关政策 ---\n");
            for (int i = 0; i < docs.size(); i++) {
                sb.append(String.format("[政策 %d]\n%s\n\n", i + 1, docs.get(i).getContent()));
            }
            sb.append("--- 检索结束 ---\n\n");
        }
        if (message.getData() != null && !message.getData().isEmpty()) {
            sb.append("补充信息:\n");
            message.getData().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        return sb.toString();
    }
}
