package com.aerofin.agent.observer;

import com.aerofin.agent.AgentRole;
import com.aerofin.agent.BaseAgent.AgentState;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent published every time an agent's state changes.
 * <p>
 * Using Spring's event bus (Observer pattern) decouples the agents from
 * any monitoring or orchestration logic that reacts to state transitions.
 */
@Getter
public class AgentStateEvent extends ApplicationEvent {

    private final AgentRole role;
    private final AgentState previousState;
    private final AgentState newState;
    private final String sessionId;

    public AgentStateEvent(Object source, AgentRole role,
                           AgentState previousState, AgentState newState,
                           String sessionId) {
        super(source);
        this.role = role;
        this.previousState = previousState;
        this.newState = newState;
        this.sessionId = sessionId;
    }

    @Override
    public String toString() {
        return String.format("AgentStateEvent[%s: %s -> %s, session=%s]",
                role.getName(), previousState, newState, sessionId);
    }
}
