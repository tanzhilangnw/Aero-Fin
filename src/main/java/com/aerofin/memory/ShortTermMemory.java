package com.aerofin.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-term memory: in-process sliding window of recent conversation turns.
 * <p>
 * <b>Layered Memory design (interview talking point):</b>
 * <ul>
 *   <li>Short-term memory holds the last N messages in-process — zero latency,
 *       lost on restart.  It is the "working memory" of the agent.</li>
 *   <li>Long-term memory ({@link LongTermMemory}) is stored in Redis — survives
 *       restarts, shared across instances, searchable by session.</li>
 * </ul>
 * Keeping the two layers separate means the hot path (LLM call) only touches
 * the in-process map while persistence happens asynchronously.
 */
public class ShortTermMemory {

    private final int windowSize;
    private final ConcurrentHashMap<String, List<Message>> sessions = new ConcurrentHashMap<>();

    public ShortTermMemory(int windowSize) {
        this.windowSize = windowSize;
    }

    /** Appends a message to the session window, evicting the oldest if needed. */
    public void add(String sessionId, Message message) {
        sessions.compute(sessionId, (id, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(message);
            if (list.size() > windowSize) {
                list = new ArrayList<>(list.subList(list.size() - windowSize, list.size()));
            }
            return list;
        });
    }

    /** Returns an unmodifiable view of the current window for a session. */
    public List<Message> get(String sessionId) {
        return Collections.unmodifiableList(
                sessions.getOrDefault(sessionId, Collections.emptyList()));
    }

    /** Clears the in-memory window for a session (e.g. on explicit reset). */
    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }

    public int windowSize() {
        return windowSize;
    }
}
