package com.aerofin.agent;

import com.aerofin.config.AgentSystemPrompts;
import com.aerofin.skill.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Coordinator Agent — intent identification and routing.
 * <p>
 * Routing is now delegated to {@link SkillRegistry}, eliminating all
 * hard-coded if-else keyword chains (Strategy Pattern).
 */
@Slf4j
@Component
public class CoordinatorAgent extends BaseAgent {

    private final SkillRegistry skillRegistry;

    public CoordinatorAgent(ChatClient chatClient,
                            ApplicationEventPublisher eventPublisher,
                            SkillRegistry skillRegistry) {
        super(AgentRole.COORDINATOR, chatClient, eventPublisher);
        this.skillRegistry = skillRegistry;
        log.info("[CoordinatorAgent] Initialized");
    }

    @Override
    public Mono<AgentMessage> handleMessage(AgentMessage message) {
        return Mono.fromCallable(() -> {
            String content = message.getContent();
            List<com.aerofin.skill.Skill> matched = skillRegistry.selectSkills(content);
            boolean multiAgent = matched.size() > 1;

            AgentMessage result = message.createResponse(
                    multiAgent ? "Multi-skill plan required" : "Single-skill routing");

            if (multiAgent) {
                List<AgentRole> roles = matched.stream()
                        .map(com.aerofin.skill.Skill::getAgentRole)
                        .toList();
                result.addData("requiresMultiAgent", true);
                result.addData("requiredAgents", roles);
            } else {
                AgentRole target = matched.isEmpty()
                        ? AgentRole.LOAN_EXPERT
                        : matched.get(0).getAgentRole();
                AgentMessage routing = AgentMessage.createTaskAssignment(
                        AgentRole.COORDINATOR, target, content, message.getSessionId());
                if (message.getData() != null) routing.getData().putAll(message.getData());
                result.addData("requiresMultiAgent", false);
                result.addData("targetAgent", target.name());
                result.addData("routingMessage", routing);
            }
            return result;
        });
    }

    @Override
    public Flux<String> handleMessageStream(AgentMessage message) {
        return Flux.defer(() -> {
            AgentRole target = skillRegistry.selectPrimarySkill(message.getContent())
                    .map(com.aerofin.skill.Skill::getAgentRole)
                    .orElse(AgentRole.LOAN_EXPERT);
            return Flux.just(String.format("Routing to [%s]...\n", target.getName()));
        });
    }

    @Override
    protected String getSystemPrompt() { return AgentSystemPrompts.SUPERVISOR_PROMPT; }

    @Override
    protected List<String> getAvailableTools() { return List.of(); }

    // ── Kept for backward compatibility with any callers ──────────────────────

    public AgentRole identifyIntent(String userMessage) {
        return skillRegistry.selectPrimarySkill(userMessage)
                .map(com.aerofin.skill.Skill::getAgentRole)
                .orElse(AgentRole.LOAN_EXPERT);
    }

    public boolean requiresMultiAgent(String userMessage) {
        return skillRegistry.selectSkills(userMessage).size() > 1;
    }

    public List<AgentRole> identifyRequiredAgents(String userMessage) {
        return skillRegistry.selectSkills(userMessage).stream()
                .map(com.aerofin.skill.Skill::getAgentRole)
                .toList();
    }
}
