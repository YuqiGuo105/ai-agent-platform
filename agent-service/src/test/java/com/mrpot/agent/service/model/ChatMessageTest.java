package com.mrpot.agent.service.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageTest {

    @Test
    void user_createsUserMessage() {
        ChatMessage message = ChatMessage.user("test content");
        
        assertEquals("user", message.role());
        assertEquals("test content", message.content());
        assertNotNull(message.timestamp());
    }

    @Test
    void assistant_createsAssistantMessage() {
        ChatMessage message = ChatMessage.assistant("test response");
        
        assertEquals("assistant", message.role());
        assertEquals("test response", message.content());
        assertNotNull(message.timestamp());
    }

    @Test
    void isUser_returnsTrue_forUserRole() {
        ChatMessage message = new ChatMessage("user", "content", Instant.now());
        assertTrue(message.isUser());
        assertFalse(message.isAssistant());
    }

    @Test
    void isUser_returnsTrueIgnoringCase() {
        ChatMessage message = new ChatMessage("USER", "content", Instant.now());
        assertTrue(message.isUser());
    }

    @Test
    void isAssistant_returnsTrue_forAssistantRole() {
        ChatMessage message = new ChatMessage("assistant", "content", Instant.now());
        assertTrue(message.isAssistant());
        assertFalse(message.isUser());
    }

    @Test
    void isAssistant_returnsTrueIgnoringCase() {
        ChatMessage message = new ChatMessage("ASSISTANT", "content", Instant.now());
        assertTrue(message.isAssistant());
    }

    @Test
    void record_equalityWorks() {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        ChatMessage message1 = new ChatMessage("user", "hello", timestamp);
        ChatMessage message2 = new ChatMessage("user", "hello", timestamp);
        ChatMessage message3 = new ChatMessage("user", "different", timestamp);
        
        assertEquals(message1, message2);
        assertNotEquals(message1, message3);
    }

    @Test
    void record_hashCodeWorks() {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        ChatMessage message1 = new ChatMessage("user", "hello", timestamp);
        ChatMessage message2 = new ChatMessage("user", "hello", timestamp);
        
        assertEquals(message1.hashCode(), message2.hashCode());
    }

    @Test
    void constructor_acceptsAllParameters() {
        Instant timestamp = Instant.parse("2026-02-10T15:30:00Z");
        ChatMessage message = new ChatMessage("system", "system message", timestamp);
        
        assertEquals("system", message.role());
        assertEquals("system message", message.content());
        assertEquals(timestamp, message.timestamp());
    }

    @Test
    void isUser_returnsFalse_forOtherRoles() {
        ChatMessage message = new ChatMessage("system", "content", Instant.now());
        assertFalse(message.isUser());
        assertFalse(message.isAssistant());
    }
}
