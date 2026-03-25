package com.aerofin.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Long-term memory: Redis-backed persistent conversation store.
 * <p>
 * <b>Layered Memory design (interview talking point):</b>
 * <ul>
 *   <li>Stores serialised turn summaries (not full messages) to keep Redis
 *       payloads small.  Full messages live in {@link ShortTermMemory}.</li>
 *   <li>Key pattern: {@code aerofin:memory:{sessionId}} → Redis List.</li>
 *   <li>TTL: 7 days — sessions older than that are considered cold and purged.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LongTermMemory {

    private static final String KEY_PREFIX = "aerofin:memory:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_ENTRIES = 200;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Persists a turn summary (one string per exchange) to the session's
     * long-term store.  Older entries beyond {@code MAX_ENTRIES} are trimmed.
     */
    public void append(String sessionId, String turnSummary) {
        String key = key(sessionId);
        try {
            redisTemplate.opsForList().rightPush(key, turnSummary);
            redisTemplate.opsForList().trim(key, -MAX_ENTRIES, -1);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("[LongTermMemory] Failed to persist turn summary for session={}", sessionId, e);
        }
    }

    /**
     * Retrieves all stored turn summaries for a session, oldest-first.
     */
    @SuppressWarnings("unchecked")
    public List<String> retrieve(String sessionId) {
        try {
            List<Object> raw = redisTemplate.opsForList().range(key(sessionId), 0, -1);
            if (raw == null) return Collections.emptyList();
            List<String> result = new ArrayList<>(raw.size());
            raw.forEach(o -> result.add(o.toString()));
            return result;
        } catch (Exception e) {
            log.warn("[LongTermMemory] Failed to retrieve memory for session={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /** Returns a condensed string of the last {@code n} summaries. */
    public String recallRecent(String sessionId, int n) {
        List<String> all = retrieve(sessionId);
        int from = Math.max(0, all.size() - n);
        return String.join("\n", all.subList(from, all.size()));
    }

    public void clear(String sessionId) {
        redisTemplate.delete(key(sessionId));
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
