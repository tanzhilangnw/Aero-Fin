package com.aerofin.exception;

/**
 * 工具调用超时异常
 *
 * @author Aero-Fin Team
 */
public class ToolTimeoutException extends AeroFinException {

    public ToolTimeoutException(String toolName, long timeoutMs) {
        super("TOOL_TIMEOUT", String.format("Tool '%s' execution timeout after %dms", toolName, timeoutMs));
    }

    public ToolTimeoutException(String toolName, long timeoutMs, Throwable cause) {
        super("TOOL_TIMEOUT", String.format("Tool '%s' execution timeout after %dms", toolName, timeoutMs), cause);
    }
}
