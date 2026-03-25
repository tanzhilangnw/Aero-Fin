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
 * Skill: Customer Service
 * <p>
 * Domain knowledge: penalty waiver applications, payment date changes,
 * complaint handling.  Follows a strict SOP (resource eligibility check
 * → risk disclosure → user confirmation → execution).
 * Required MCP tools: {@code applyWaiver}, {@code queryWaiverStatus}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceSkill implements Skill {

    private final ChatClient chatClient;
    private final McpClientAdapter mcpClient;

    private static final SkillDescriptor DESCRIPTOR = new SkillDescriptor(
            "CustomerService",
            "Handles complaints, penalty waiver applications, and account service requests.",
            List.of(
                    Pattern.compile("投诉|减免|罚息|申请|办理|修改|查询状态|还款日"),
                    Pattern.compile("客服|人工|服务|申诉")
            ),
            80
    );

    @Override
    public SkillDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentRole getAgentRole() {
        return AgentRole.CUSTOMER_SERVICE;
    }

    @Override
    public String buildSystemPrompt(Map<String, Object> context) {
        return AgentSystemPrompts.ACTION_SOP_PROMPT;
    }

    @Override
    public List<String> getRequiredTools() {
        return List.of("applyWaiver", "queryWaiverStatus");
    }

    @Override
    public Mono<String> execute(String userMessage, String sessionId, Map<String, Object> context) {
        return Mono.fromCallable(() ->
                chatClient.prompt()
                        .system(buildSystemPrompt(context))
                        .user(userMessage)
                        .functions("applyWaiver", "queryWaiverStatus")
                        .call()
                        .content()
        );
    }

    @Override
    public Flux<String> executeStream(String userMessage, String sessionId, Map<String, Object> context) {
        return chatClient.prompt()
                .system(buildSystemPrompt(context))
                .user(userMessage)
                .functions("applyWaiver", "queryWaiverStatus")
                .stream()
                .content();
    }
}
