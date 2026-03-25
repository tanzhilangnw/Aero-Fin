package com.aerofin.skill;

import com.aerofin.agent.AgentRole;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * A Skill encapsulates a domain-specific capability: its system prompt,
 * the MCP tool names it requires, and the logic to execute a task.
 * <p>
 * Interview highlights:
 * <ul>
 *   <li><b>Strategy Pattern</b> — the Orchestrator selects a Skill by matching
 *       the user intent against {@link #getDescriptor()}, then delegates
 *       execution to the chosen Skill without any if-else branching.</li>
 *   <li><b>Self-contained domain knowledge</b> — each Skill owns its prompt
 *       and tool list, so adding a new domain means adding one class.</li>
 * </ul>
 */
public interface Skill {

    /** Metadata used by the planner to select this skill. */
    SkillDescriptor getDescriptor();

    /** The AgentRole this skill is associated with. */
    AgentRole getAgentRole();

    /**
     * System prompt injected into the LLM when this skill is active.
     * May be enriched with RAG context at runtime.
     */
    String buildSystemPrompt(Map<String, Object> context);

    /** Names of the MCP tools this skill requires. */
    List<String> getRequiredTools();

    /** Execute the skill and return a single aggregated response. */
    Mono<String> execute(String userMessage, String sessionId, Map<String, Object> context);

    /** Execute the skill as a reactive stream. */
    Flux<String> executeStream(String userMessage, String sessionId, Map<String, Object> context);
}
