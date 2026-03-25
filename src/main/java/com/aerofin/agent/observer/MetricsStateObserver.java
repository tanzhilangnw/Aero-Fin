package com.aerofin.agent.observer;

import com.aerofin.agent.BaseAgent.AgentState;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer that records agent state transitions as Micrometer metrics.
 * <p>
 * Listening via Spring's {@code @EventListener} means this class is completely
 * decoupled from {@code BaseAgent} — adding a new observer never requires
 * touching agent code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsStateObserver implements AgentStateObserver {

    private final MeterRegistry meterRegistry;

    @Override
    @EventListener
    public void onStateChanged(AgentStateEvent event) {
        // Count every ERROR transition as an agent error metric
        if (event.getNewState() == AgentState.ERROR) {
            meterRegistry.counter(
                    "aerofin.agent.errors",
                    "role", event.getRole().name()
            ).increment();
        }
        // Count completions
        if (event.getNewState() == AgentState.COMPLETED) {
            meterRegistry.counter(
                    "aerofin.agent.completions",
                    "role", event.getRole().name()
            ).increment();
        }
        log.debug("[MetricsObserver] {}", event);
    }
}
