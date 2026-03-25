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
 * Skill: Loan Calculator
 * <p>
 * Domain knowledge: compound-interest repayment schedules, early repayment
 * penalties, instalment comparison.
 * Required MCP tools: {@code calculateLoan}, {@code estimateLateFee},
 * {@code calculateEarlyRepayment}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanCalculatorSkill implements Skill {

    private final ChatClient chatClient;
    private final McpClientAdapter mcpClient;

    private static final SkillDescriptor DESCRIPTOR = new SkillDescriptor(
            "LoanCalculator",
            "Calculates monthly repayments, total interest, and early-repayment costs.",
            List.of(
                    Pattern.compile("贷款|月供|利率|还款|本金|利息|计算|逾期|罚息|提前还"),
                    Pattern.compile("[0-9]+\\s*万|[0-9]+\\s*元"),
                    Pattern.compile("期|年|月")
            ),
            90
    );

    @Override
    public SkillDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentRole getAgentRole() {
        return AgentRole.LOAN_EXPERT;
    }

    @Override
    public String buildSystemPrompt(Map<String, Object> context) {
        return AgentSystemPrompts.CALCULATOR_PROMPT;
    }

    @Override
    public List<String> getRequiredTools() {
        return List.of("calculateLoan", "estimateLateFee", "calculateEarlyRepayment");
    }

    @Override
    public Mono<String> execute(String userMessage, String sessionId, Map<String, Object> context) {
        return Mono.fromCallable(() -> {
            log.info("[LoanCalculatorSkill] Executing for session={}", sessionId);
            return chatClient.prompt()
                    .system(buildSystemPrompt(context))
                    .user(userMessage)
                    .functions("calculateLoan")
                    .call()
                    .content();
        });
    }

    @Override
    public Flux<String> executeStream(String userMessage, String sessionId, Map<String, Object> context) {
        return chatClient.prompt()
                .system(buildSystemPrompt(context))
                .user(userMessage)
                .functions("calculateLoan")
                .stream()
                .content();
    }
}
