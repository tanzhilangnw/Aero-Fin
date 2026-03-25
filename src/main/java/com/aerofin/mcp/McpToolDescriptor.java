package com.aerofin.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Descriptor for a single tool exposed by an MCP-compatible server.
 * Loaded dynamically from JSON so no recompilation is needed when a new
 * MCP server is added (see {@link McpClientAdapter#registerToolFromJson}).
 */
public record McpToolDescriptor(
        String name,
        String description,
        JsonNode inputSchema
) {
    /** One-line declaration for injection into an LLM system prompt. */
    public String toPromptDeclaration() {
        return String.format("- %s: %s", name, description);
    }
}
