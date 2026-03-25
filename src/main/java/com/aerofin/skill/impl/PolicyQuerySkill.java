package com.aerofin.skill.impl;

import com.aerofin.agent.AgentRole;
import com.aerofin.config.AgentSystemPrompts;
import com.aerofin.mcp.McpClientAdapter;
import com.aerofin.service.VectorSearchService;
import com.aerofin.skill.Skill;
import com.aerofin.skill.SkillDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Skill: Policy Query (RAG)
 * <p>
 * Domain knowledge: credit policies, eligibility conditions, promotional
 * activities.  Enriches the system prompt with retrieved vector-store
 * documents before calling the LLM (Retrieval-Augmented Generation).
 * Required MCP tools: {@code queryPolicy}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyQuerySkill implements Skill {

    private final ChatClient chatClient;
    private final McpClientAdapter mcpClient;
    private final VectorSearchService vectorSearchService;

    private static final SkillDescriptor DESCRIPTOR = new SkillDescriptor(
            "PolicyQuery",
            "Retrieves and interprets credit policies, eligibility rules, and promotions via RAG.",
            List.of(
                    Pattern.compile("政策|优惠|条件|活动|要求|规定|利率表|额度"),
                    Pattern.compile("申请条件|贷款条件|资格|章程|规则")
            ),
            85
    );

    @Override
    public SkillDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentRole getAgentRole() {
        return AgentRole.POLICY_EXPERT;
    }

    /**
     * Builds the system prompt enriched with RAG context.
     * The retrieved documents are injected under a "--- Retrieved Policies ---"
     * section so the LLM can cite them directly.
     */
    @Override
    public String buildSystemPrompt(Map<String, Object> context) {
        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) context.getOrDefault("retrievedDocs", List.of());
        if (docs.isEmpty()) {
            return AgentSystemPrompts.POLICY_RAG_PROMPT;
        }
        StringBuilder sb = new StringBuilder(AgentSystemPrompts.POLICY_RAG_PROMPT);
        sb.append("\n\n--- Retrieved Policies ---\n");
        for (int i = 0; i < docs.size(); i++) {
            sb.append(String.format("[Policy %d]\n%s\n\n", i + 1, docs.get(i).getContent()));
        }
        sb.append("--- End of Retrieved Policies ---\n");
        return sb.toString();
    }

    @Override
    public List<String> getRequiredTools() {
        return List.of("queryPolicy");
    }

    @Override
    public Mono<String> execute(String userMessage, String sessionId, Map<String, Object> context) {
        return Mono.fromCallable(() -> {
            List<Document> docs = vectorSearchService.searchRelevantPolicies(userMessage);
            context.put("retrievedDocs", docs);
            log.info("[PolicyQuerySkill] RAG retrieved {} docs for session={}", docs.size(), sessionId);
            return chatClient.prompt()
                    .system(buildSystemPrompt(context))
                    .user(userMessage)
                    .functions("queryPolicy")
                    .call()
                    .content();
        });
    }

    @Override
    public Flux<String> executeStream(String userMessage, String sessionId, Map<String, Object> context) {
        List<Document> docs = vectorSearchService.searchRelevantPolicies(userMessage);
        context.put("retrievedDocs", docs);
        return chatClient.prompt()
                .system(buildSystemPrompt(context))
                .user(userMessage)
                .functions("queryPolicy")
                .stream()
                .content();
    }

    /** Exposed for orchestrator pre-checks (e.g. empty-RAG detection). */
    public List<Document> retrieveDocs(String query) {
        return vectorSearchService.searchRelevantPolicies(query);
    }
}
