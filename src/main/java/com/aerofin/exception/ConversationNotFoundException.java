package com.aerofin.exception;

/**
 * 会话不存在异常
 *
 * @author Aero-Fin Team
 */
public class ConversationNotFoundException extends AeroFinException {

    public ConversationNotFoundException(String sessionId) {
        super("CONVERSATION_NOT_FOUND", String.format("Conversation session '%s' not found", sessionId));
    }
}
