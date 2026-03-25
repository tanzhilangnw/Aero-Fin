package com.aerofin.agent;

import com.aerofin.agent.experts.CorrectorAgent;
import com.aerofin.agent.experts.ReflectAgent;
import com.aerofin.agent.plan.AgentPlan;
import com.aerofin.agent.plan.ReActPlanner;
import com.aerofin.memory.LongTermMemory;
import com.aerofin.memory.ShortTermMemory;
import com.aerofin.skill.Skill;
import com.aerofin.skill.SkillRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State-based multi-agent orchestrator with ReAct planning.
 *
 * Architecture:
 * <pre>
 * User Request
 *      ↓
 * ReActPlanner  --&gt;  AgentPlan (ordered PlanSteps)
 *      ↓
 * Step 1: Skill.execute()  --&gt; output injected as context into Step 2
 * Step 2: Skill.execute()  ...
 *      ↓
 * (optional) ReflectAgent review
 *      ↓
 * Final Response
 * </pre>
 *
 * Interview talking points:
 * - Strategy Pattern: all domain routing via SkillRegistry.selectSkills() — zero if-else.
 * - Observer Pattern: agent state changes published as Spring events; Orchestrator never inspects state.
 * - Layered Memory: short-term (in-process sliding window) + long-term (Redis) updated each exchange.
 * - Tool-result hash cache: transparent in McpClientAdapter (SHA-256 keyed, saves LLM tokens).
 * - ReAct multi-step: step N output fed as "priorStepOutput" context into step N+1 prompt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    private final SkillRegistry skillRegistry;
    private final ReActPlanner planner;
    private final ReflectAgent reflectAgent;
    private final CorrectorAgent correctorAgent;
    private final ShortTermMemory shortTermMemory;
    private final LongTermMemory longTermMemory;

    /** All BaseAgent beans — used only for the legacy status/metrics API. */
    private final List<BaseAgent> allAgents;
    private final Map<AgentRole, BaseAgent> agentRegistry = new HashMap<>();

    @PostConstruct
    public void initialize() {
        allAgents.forEach(agent -> agentRegistry.put(agent.getRole(), agent));
        log.info("[Orchestrator] Ready — {} agents, {} skills",
                agentRegistry.size(), skillRegistry.allSkills().size());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Non-streaming execution.
     * The planner selects skills; multi-step plans run sequentially
     * with each step's output fed as context into the next (ReAct chain).
     */
    public Mono<String> processRequest(String userMessage, String sessionId, String userId) {
        log.info("[Orchestrator] Request session={} user={}", sessionId, userId);
        AgentPlan plan = planner.plan(userMessage);
        return executePlan(plan, sessionId, userId)
                .doOnNext(answer -> persistMemory(sessionId, userMessage, answer));
    }

    /**
     * Non-streaming with ReflectAgent compliance review.
     */
    public Mono<String> processRequestWithReflection(String userMessage, String sessionId, String userId) {
        return processRequest(userMessage, sessionId, userId)
                .flatMap(draft -> {
                    AgentMessage msg = AgentMessage.createTaskAssignment(
                            AgentRole.COORDINATOR, AgentRole.REFLECTOR,
                            "请审阅以下回答的合规性与风险提示是否充分。", sessionId);
                    msg.addData("userId", userId);
                    msg.addData("userQuestion", userMessage);
                    msg.addData("draftAnswer", draft);
                    msg.addData("sourceAgent", "AUTO");
                    return reflectAgent.execute(msg).map(AgentMessage::getContent);
                });
    }

    /**
     * Streaming execution.
     * Single-step plans stream directly; multi-step plans run the chain
     * non-reactively and emit the aggregated result as a single Flux.
     */
    public Flux<String> processRequestStream(String userMessage, String sessionId, String userId) {
        log.info("[Orchestrator] Stream session={}", sessionId);
        return Flux.defer(() -> {
            AgentPlan plan = planner.plan(userMessage);
            if (plan.isMultiStep()) {
                return Flux.just("正在协调多位专家为您服务...\n\n")
                        .concatWith(
                            executePlan(plan, sessionId, userId)
                                .doOnNext(a -> persistMemory(sessionId, userMessage, a))
                                .flatMapMany(Flux::just)
                        );
            }
            AgentPlan.PlanStep step = plan.getSteps().get(0);
            Optional<Skill> skill = skillRegistry.findByName(step.skillName());
            if (skill.isEmpty()) {
                return Flux.just("未找到匹配的技能，请稍后重试。");
            }
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("userId", userId);
            return skill.get().executeStream(userMessage, sessionId, ctx);
        });
    }

    /**
     * Kept for backward-compatibility — delegates to processRequest.
     */
    public Mono<String> processMultiAgentRequest(String userMessage, String sessionId, String userId) {
        return processRequest(userMessage, sessionId, userId);
    }

    // ── Plan execution (ReAct chain) ──────────────────────────────────────────

    /**
     * Walks the plan steps sequentially.
     * Each step receives the prior step's output as "priorStepOutput" in its
     * context map, enabling chain-of-thought reasoning across domain experts.
     */
    private Mono<String> executePlan(AgentPlan plan, String sessionId, String userId) {
        List<AgentPlan.PlanStep> steps = plan.getSteps();
        if (steps.isEmpty()) {
            return Mono.just("没有可执行的计划步骤。");
        }

        Mono<String> chain = Mono.just("");
        for (int i = 0; i < steps.size(); i++) {
            final int idx = i;
            chain = chain.flatMap(priorOutput -> {
                AgentPlan.PlanStep step = steps.get(idx);
                Optional<Skill> skill = skillRegistry.findByName(step.skillName());
                if (skill.isEmpty()) {
                    log.warn("[Orchestrator] Skill '{}' not found — skipping", step.skillName());
                    return Mono.just(priorOutput);
                }
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("userId", userId);
                if (!priorOutput.isBlank()) {
                    // ReAct: inject previous Observation into current Action context
                    ctx.put("priorStepOutput", priorOutput);
                }
                String enrichedQuery = enrichWithShortTermMemory(step.subQuery(), sessionId);
                return skill.get().execute(enrichedQuery, sessionId, ctx)
                        .onErrorResume(err -> {
                            log.warn("[Orchestrator] Skill {} error: {}", step.skillName(), err.getMessage());
                            return runCorrectionFlow(step.subQuery(), sessionId, step.agentRole())
                                    .flatMap(corrected -> skill.get().execute(corrected, sessionId, ctx))
                                    .onErrorReturn("技能执行失败，请稍后重试。");
                        })
                        .map(output -> steps.size() == 1
                                ? output
                                : formatMultiStepResult(idx + 1, step.skillName(), priorOutput, output));
            });
        }
        return chain;
    }

    // ── Correction flow ───────────────────────────────────────────────────────

    private Mono<String> runCorrectionFlow(String originalQuery, String sessionId, AgentRole failedRole) {
        AgentMessage msg = AgentMessage.createTaskAssignment(
                AgentRole.COORDINATOR, AgentRole.CORRECTOR,
                "请分析任务失败原因并提供修正计划。", sessionId);
        msg.addData("originalQuery", originalQuery);
        msg.addData("failedAgent", failedRole.name());
        msg.addData("failureReason", "SKILL_EXECUTION_ERROR");
        return correctorAgent.execute(msg)
                .flatMap(result -> {
                    if (result.getMessageType() == AgentMessage.MessageType.TASK_FAILED) {
                        return Mono.error(new RuntimeException("Correction flow failed"));
                    }
                    String suggestedQuery = result.getData("suggestedQuery", String.class);
                    return Mono.just(suggestedQuery != null ? suggestedQuery : originalQuery);
                });
    }

    // ── Memory ────────────────────────────────────────────────────────────────

    /**
     * Prepends relevant short-term history to the query.
     * Short-term memory holds the in-process sliding window of recent messages.
     */
    private String enrichWithShortTermMemory(String query, String sessionId) {
        var recentMessages = shortTermMemory.get(sessionId);
        if (recentMessages.isEmpty()) return query;
        StringBuilder sb = new StringBuilder();
        sb.append("[近期对话上下文]\n");
        recentMessages.forEach(m -> sb.append(m.getContent()).append("\n"));
        sb.append("[当前问题] ").append(query);
        return sb.toString();
    }

    /**
     * Persists exchange to both memory layers after each response.
     * - Short-term: in-process Spring AI Message object
     * - Long-term: Redis one-line turn summary
     */
    private void persistMemory(String sessionId, String userMessage, String answer) {
        try {
            // Short-term: store as plain user/assistant text messages
            shortTermMemory.add(sessionId,
                    new org.springframework.ai.chat.messages.UserMessage(userMessage));
            shortTermMemory.add(sessionId,
                    new org.springframework.ai.chat.messages.AssistantMessage(answer));
            // Long-term: compact summary
            String summary = String.format("Q: %s | A: %s",
                    truncate(userMessage, 80), truncate(answer, 120));
            longTermMemory.append(sessionId, summary);
        } catch (Exception e) {
            log.warn("[Orchestrator] Memory persist failed for session={}", sessionId, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatMultiStepResult(int stepNum, String skillName,
                                          String priorOutput, String output) {
        StringBuilder sb = new StringBuilder();
        if (!priorOutput.isBlank()) sb.append(priorOutput).append("\n\n");
        sb.append(String.format("[Step %d — %s]\n%s", stepNum, skillName, output));
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ── Legacy status/metrics API ─────────────────────────────────────────────

    public Map<String, Object> getAgentStatusSummary() {
        Map<String, Object> summary = new HashMap<>();
        agentRegistry.forEach((role, agent) -> {
            Map<String, Object> status = new HashMap<>();
            status.put("state", agent.getState().name());
            status.put("totalProcessed", agent.getMetrics().get("totalProcessed"));
            status.put("totalErrors", agent.getMetrics().get("totalErrors"));
            status.put("avgResponseTime", agent.getAverageResponseTime());
            summary.put(role.getName(), status);
        });
        return summary;
    }

    public BaseAgent getAgent(AgentRole role) {
        return agentRegistry.get(role);
    }

    public void resetAllMetrics() {
        agentRegistry.values().forEach(BaseAgent::resetMetrics);
        log.info("[Orchestrator] All agent metrics reset");
    }
}
