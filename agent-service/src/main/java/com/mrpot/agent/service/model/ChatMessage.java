package com.mrpot.agent.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Represents a chat message in conversation history.
 * 
 * @param role      the message role: "user" or "assistant"
 * @param content   the message content
 * @param timestamp the time when the message was created
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(
    String role,
    String content,
    Instant timestamp
) {
    /**
     * Create a user message.
     *
     * @param content the message content
     * @return a new ChatMessage with role "user"
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, Instant.now());
    }

    /**
     * Create an assistant message.
     *
     * @param content the message content
     * @return a new ChatMessage with role "assistant"
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, Instant.now());
    }

    /**
     * Check if this is a user message.
     *
     * @return true if role is "user"
     */
    public boolean isUser() {
        return "user".equalsIgnoreCase(role);
    }

    /**
     * Check if this is an assistant message.
     *
     * @return true if role is "assistant"
     */
    public boolean isAssistant() {
        return "assistant".equalsIgnoreCase(role);
    }
}
