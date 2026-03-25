package com.aerofin.agent;

import com.aerofin.agent.observer.AgentStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent base class.
 * <p>
 * Design highlights:
 * <ul>
 *   <li><b>Template Method Pattern</b> — {@link #execute} / {@link #executeStream}
 *       define the lifecycle; subclasses only override {@link #handleMessage} /
 *       {@link #handleMessageStream}.</li>
 *   <li><b>Observer Pattern</b> — every state transition publishes an
 *       {@link AgentStateEvent} via Spring's {@link ApplicationEventPublisher}.
 *       Listeners (e.g. {@code MetricsStateObserver}) are completely decoupled
 *       from this class; no if-else fan-out needed here.</li>
 * </ul>
 */
@Slf4j
public abstract class BaseAgent {

    protected final AgentRole role;
    protected final ChatClient chatClient;

    /** Spring event bus — injected by subclasses that are Spring beans. */
    protected final ApplicationEventPublisher eventPublisher;

    protected volatile AgentState state = AgentState.IDLE;
    protected final ConcurrentMap<String, Long> metrics = new ConcurrentHashMap<>();

    /** Current session ID — set during execute() so observers can include it. */
    private volatile String currentSessionId = "";

    public enum AgentState {
        IDLE, PROCESSING, WAITING, ERROR, COMPLETED
    }

    protected BaseAgent(AgentRole role, ChatClient chatClient,
                        ApplicationEventPublisher eventPublisher) {
        this.role = role;
        this.chatClient = chatClient;
        this.eventPublisher = eventPublisher;
        initializeMetrics();
    }

    // ── Abstract contract ────────────────────────────────────────────────────

    public abstract Mono<AgentMessage> handleMessage(AgentMessage message);
    public abstract Flux<String> handleMessageStream(AgentMessage message);
    protected abstract String getSystemPrompt();
    protected abstract List<String> getAvailableTools();

    // ── Template methods ─────────────────────────────────────────────────────

    public Mono<AgentMessage> execute(AgentMessage message) {
        return Mono.defer(() -> {
            currentSessionId = message.getSessionId();
            preProcess(message);
            return handleMessage(message)
                    .doOnNext(result -> postProcess(message, result))
                    .doOnError(error -> handleError(message, error));
        });
    }

    public Flux<String> executeStream(AgentMessage message) {
        return Flux.defer(() -> {
            currentSessionId = message.getSessionId();
            preProcess(message);
            return handleMessageStream(message)
                    .doOnComplete(() -> postProcess(message, null))
                    .doOnError(error -> handleError(message, error));
        });
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    protected void preProcess(AgentMessage message) {
        log.info("[{}] Received message: {} from {}",
                role.getName(), message.getMessageId(), message.getSender().getName());
        setState(AgentState.PROCESSING);
        metrics.put("lastStartTime", System.currentTimeMillis());
        metrics.merge("totalProcessed", 1L, Long::sum);
    }

    protected void postProcess(AgentMessage message, AgentMessage result) {
        long duration = System.currentTimeMillis() - metrics.get("lastStartTime");
        log.info("[{}] Completed message: {} in {}ms",
                role.getName(), message.getMessageId(), duration);
        setState(AgentState.IDLE);
        metrics.merge("totalResponseTime", duration, Long::sum);
        metrics.put("lastResponseTime", duration);
    }

    protected void handleError(AgentMessage message, Throwable error) {
        log.error("[{}] Error processing message: {}",
                role.getName(), message.getMessageId(), error);
        setState(AgentState.ERROR);
        metrics.merge("totalErrors", 1L, Long::sum);
    }

    // ── State management (Observer pattern) ─────────────────────────────────

    /**
     * Transitions to {@code newState} and publishes an {@link AgentStateEvent}
     * so that any registered Spring listener can react without coupling.
     */
    protected void setState(AgentState newState) {
        AgentState previous = this.state;
        if (previous == newState) return;
        log.debug("[{}] State: {} -> {}", role.getName(), previous, newState);
        this.state = newState;
        if (eventPublisher != null) {
            eventPublisher.publishEvent(
                    new AgentStateEvent(this, role, previous, newState, currentSessionId));
        }
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public boolean canHandle(AgentMessage message) { return message.getReceiver() == this.role; }
    public AgentRole getRole()   { return role; }
    public AgentState getState() { return state; }
    public boolean isIdle()      { return state == AgentState.IDLE; }

    public ConcurrentMap<String, Long> getMetrics() {
        return new ConcurrentHashMap<>(metrics);
    }

    public long getAverageResponseTime() {
        Long totalTime = metrics.get("totalResponseTime");
        Long totalProcessed = metrics.get("totalProcessed");
        if (totalTime == null || totalProcessed == null || totalProcessed == 0) return 0L;
        return totalTime / totalProcessed;
    }

    public void resetMetrics() {
        initializeMetrics();
        log.info("[{}] Metrics reset", role.getName());
    }

    private void initializeMetrics() {
        metrics.put("totalProcessed", 0L);
        metrics.put("totalResponseTime", 0L);
        metrics.put("totalErrors", 0L);
        metrics.put("lastStartTime", 0L);
        metrics.put("lastResponseTime", 0L);
    }
}
