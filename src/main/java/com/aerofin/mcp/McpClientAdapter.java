package com.aerofin.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal adapter for any MCP-compatible tool server.
 * <p>
 * Design highlights (interview talking points):
 * <ul>
 *   <li><b>Dynamic tool loading</b> — tools are described via JSON at runtime;
 *       no recompilation needed when a new MCP server is added.</li>
 *   <li><b>Hash-based result cache</b> — tool invocations are keyed by
 *       SHA-256(toolName + canonicalArgs).  Identical calls within the same
 *       session are served from the in-process cache, saving LLM tokens.</li>
 *   <li><b>Protocol-agnostic</b> — the {@code invoke} method can be backed by
 *       HTTP, stdio, or any transport; only this class needs changing.</li>
 * </ul>
 */
@Slf4j
@Component
public class McpClientAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** In-process tool-result cache: SHA-256(tool+args) -> serialized result */
    private final Map<String, String> resultCache = new ConcurrentHashMap<>();

    /** Registry of dynamically loaded tool descriptors keyed by tool name. */
    private final Map<String, McpToolDescriptor> toolRegistry = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Tool registration (supports JSON-driven dynamic loading)
    // -------------------------------------------------------------------------

    /**
     * Registers a tool from a raw JSON descriptor — the format used by the
     * MCP {@code tools/list} response.
     *
     * <pre>{@code
     * {
     *   "name": "queryPolicy",
     *   "description": "Search financial policy documents by keyword",
     *   "inputSchema": { "type": "object", "properties": { ... } }
     * }
     * }</pre>
     */
    public void registerToolFromJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            McpToolDescriptor tool = new McpToolDescriptor(
                    node.get("name").asText(),
                    node.get("description").asText(),
                    node.get("inputSchema")
            );
            toolRegistry.put(tool.name(), tool);
            log.info("[McpClientAdapter] Registered tool: {}", tool.name());
        } catch (Exception e) {
            log.error("[McpClientAdapter] Failed to register tool from JSON", e);
        }
    }

    /**
     * Bulk-registers tools from a JSON array (the {@code tools} field of a
     * {@code tools/list} MCP response).
     */
    public void registerToolsFromJsonArray(String jsonArray) {
        try {
            JsonNode array = objectMapper.readTree(jsonArray);
            if (array.isArray()) {
                array.forEach(node -> {
                    try {
                        registerToolFromJson(node.toString());
                    } catch (Exception e) {
                        log.warn("[McpClientAdapter] Skipping malformed tool descriptor", e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("[McpClientAdapter] Failed to parse tool array", e);
        }
    }

    public List<McpToolDescriptor> listTools() {
        return List.copyOf(toolRegistry.values());
    }

    public McpToolDescriptor getTool(String name) {
        return toolRegistry.get(name);
    }

    // -------------------------------------------------------------------------
    // Tool invocation with hash cache
    // -------------------------------------------------------------------------

    /**
     * Invokes a tool by name with the given arguments.
     * <p>
     * Results are cached by {@code SHA-256(toolName + sortedArgs)}.  This
     * prevents redundant round-trips for identical queries and reduces token
     * consumption when the same policy lookup is made multiple times within a
     * planning loop.
     *
     * @param toolName name of the registered tool
     * @param args     tool arguments (will be serialized to canonical JSON)
     * @return reactive tool result
     */
    public Mono<String> invoke(String toolName, Map<String, Object> args) {
        return Mono.fromCallable(() -> {
            String cacheKey = computeCacheKey(toolName, args);

            String cached = resultCache.get(cacheKey);
            if (cached != null) {
                log.debug("[McpClientAdapter] Cache HIT for tool={} key={}", toolName, cacheKey);
                return cached;
            }

            log.debug("[McpClientAdapter] Cache MISS for tool={}, invoking...", toolName);
            String result = doInvoke(toolName, args);
            resultCache.put(cacheKey, result);
            return result;
        });
    }

    /** Evicts a single cache entry (call after data mutations). */
    public void evict(String toolName, Map<String, Object> args) {
        resultCache.remove(computeCacheKey(toolName, args));
    }

    /** Clears the entire result cache. */
    public void clearCache() {
        resultCache.clear();
        log.info("[McpClientAdapter] Tool result cache cleared");
    }

    public int cacheSize() {
        return resultCache.size();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Actual invocation logic.  In production this would dispatch over HTTP or
     * stdio to the remote MCP server.  Here we return a placeholder so the
     * rest of the system compiles and runs; replace with real transport.
     */
    private String doInvoke(String toolName, Map<String, Object> args) {
        // TODO: replace with real MCP transport (HTTP / stdio)
        log.info("[McpClientAdapter] Invoking tool={} args={}", toolName, args);
        return String.format("{\"tool\":\"%s\",\"status\":\"invoked\",\"args\":%s}",
                toolName, toJson(args));
    }

    private String computeCacheKey(String toolName, Map<String, Object> args) {
        // Canonical: sort keys so {a:1,b:2} and {b:2,a:1} hash identically
        String canonical = toolName + ":" + new java.util.TreeMap<>(args);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return canonical; // fallback
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
