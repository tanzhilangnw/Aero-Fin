package com.aerofin.agent.plan;

import com.aerofin.agent.AgentRole;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * An immutable execution plan produced by the ReAct planner.
 * <p>
 * A plan is an ordered list of {@link PlanStep}s.  Each step names one skill
 * and carries the sub-query the skill should answer.  The Orchestrator walks
 * the list sequentially, feeding each step's output as context into the next
 * (chain-of-thought, multi-step ReAct).
 */
@Getter
@Builder
public class AgentPlan {

    /** Steps to execute in order. */
    private final List<PlanStep> steps;

    /** Raw user message that triggered this plan. */
    private final String originalMessage;

    /** True when more than one skill is required. */
    public boolean isMultiStep() {
        return steps.size() > 1;
    }

    /**
     * A single step within a plan.
     *
     * @param skillName  name from {@link com.aerofin.skill.SkillDescriptor#name()}
     * @param agentRole  corresponding {@link AgentRole}
     * @param subQuery   the (possibly refined) query for this step
     * @param dependsOn  index of the step whose output this step needs, or -1
     */
    public record PlanStep(
            String skillName,
            AgentRole agentRole,
            String subQuery,
            int dependsOn
    ) {}
}
