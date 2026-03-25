package com.aerofin.skill.impl;

import com.aerofin.agent.AgentRole;
import com.aerofin.config.AgentSystemPrompts;
import com.aerofin.mcp.McpClientAdapter;
import com.aerofin.skill.Skill;
import com.aerofin.skill.SkillDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Skill: Risk Assessment
 * <p>
 * Domain knowledge: credit scoring, debt-to-income ratio evaluation,
 * fraud signal detection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAssessmentSkill implements Skill {

    private final ChatClient chatClient;
    private final McpClientAdapter mcpClient;

    private static final SkillDescriptor DESCRIPTOR = new SkillDescriptor(
            "RiskAssessment",
            "Evaluates credit risk, loan eligibility, and suggested credit limits.",
            List.of(
                    Pattern.compile("额度|审批|征信|资格|能贷|风险|评估|评级"),
                    Pattern.compile("能否|可以吗|有没有资格|白名单|黑名单")
            ),
            95
    );

    @Override
    public SkillDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentRole getAgentRole() {
        return AgentRole.RISK_ASSESSMENT;
    }

    @Override
    public String buildSystemPrompt(Map<String, Object> context) {
        return AgentSystemPrompts.RISK_ASSESSMENT_PROMPT;
    }

    @Override
    public List<String> getRequiredTools() {
        return List.of();
    }

    @Override
    public Mono<String> execute(String userMessage, String sessionId, Map<String, Object> context) {
        return Mono.fromCallable(() ->
                chatClient.prompt()
                        .system(buildSystemPrompt(context))
                        .user(userMessage)
                        .call()
                        .content()
        );
    }

    @Override
    public Flux<String> executeStream(String userMessage, String sessionId, Map<String, Object> context) {
        return chatClient.prompt()
                .system(buildSystemPrompt(context))
                .user(userMessage)
                .stream()
                .content();
    }
}
