package com.aerofin.agent.plan;

import com.aerofin.agent.AgentRole;
import com.aerofin.skill.Skill;
import com.aerofin.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct-style planner: converts a user message into an {@link AgentPlan}.
 * <p>
 * <b>Interview talking points:</b>
 * <ul>
 *   <li><b>Strategy Pattern</b> — skill selection is delegated to
 *       {@link SkillRegistry#selectSkills}, which scores every registered
 *       {@code Skill} against the message.  No if-else routing here.</li>
 *   <li><b>ReAct loop readiness</b> — the plan carries dependency indices
 *       ({@code dependsOn}) so the Orchestrator can feed step N's output as
 *       context into step N+1 (Thought → Action → Observation chain).</li>
 *   <li><b>Extensible</b> — LLM-based planning can replace or augment
 *       rule-based scoring by overriding {@link #buildFromLlm} without
 *       touching the Orchestrator.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActPlanner {

    private final SkillRegistry skillRegistry;

    /**
     * Produces an execution plan for {@code userMessage}.
     * <p>
     * When multiple skills match, their steps are ordered so that higher-scoring
     * skills run first and later steps carry a {@code dependsOn} index pointing
     * at the immediately preceding step (sequential chain-of-thought).
     */
    public AgentPlan plan(String userMessage) {
        List<Skill> matched = skillRegistry.selectSkills(userMessage);

        if (matched.isEmpty()) {
            // Fallback: route to loan expert (mirrors old CoordinatorAgent default)
            log.warn("[ReActPlanner] No skill matched — falling back to LOAN_EXPERT");
            matched = List.of(skillRegistry.findByRole(AgentRole.LOAN_EXPERT)
                    .orElseThrow(() -> new IllegalStateException("LoanCalculatorSkill not registered")));
        }

        List<AgentPlan.PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < matched.size(); i++) {
            Skill skill = matched.get(i);
            steps.add(new AgentPlan.PlanStep(
                    skill.getDescriptor().name(),
                    skill.getAgentRole(),
                    userMessage,   // each step works from the original message;
                                   // Orchestrator may refine subQuery at runtime
                    i == 0 ? -1 : i - 1  // first step has no dependency
            ));
        }

        AgentPlan plan = AgentPlan.builder()
                .originalMessage(userMessage)
                .steps(steps)
                .build();

        log.info("[ReActPlanner] Plan created: {} step(s) for message: {}",
                steps.size(), userMessage);
        return plan;
    }

    /**
     * Placeholder for LLM-based planning (e.g. function-calling to produce a
     * structured plan JSON).  Currently falls back to rule-based scoring.
     */
    protected AgentPlan buildFromLlm(String userMessage) {
        return plan(userMessage);
    }
}
