package com.aerofin.agent.observer;

/**
 * Observer interface for agent state change notifications (Observer Pattern).
 * <p>
 * Implementations are called synchronously inside {@code BaseAgent.setState()}
 * so they should be lightweight.  For heavy work, publish an async event instead.
 */
public interface AgentStateObserver {

    /**
     * Called when an agent transitions between states.
     *
     * @param event the state-change event
     */
    void onStateChanged(AgentStateEvent event);
}
