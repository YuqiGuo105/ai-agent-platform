package com.mrpot.agent.service;

import com.mrpot.agent.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LlmService.
 * Tests the buildPromptWithHistory method which doesn't require external dependencies.
 * Integration tests with Spring AI ChatClient would require full context setup.
 */
class LlmServiceTest {

    @Test
    void buildPromptWithHistory_formatsHistoryCorrectly() {
        List<ChatMessage> history = List.of(
            new ChatMessage("user", "Q1", Instant.now()),
            new ChatMessage("assistant", "A1", Instant.now()),
            new ChatMessage("user", "Q2", Instant.now()),
            new ChatMessage("assistant", "A2", Instant.now())
        );

        String result = LlmService.buildPromptWithHistory("Current question", history);

        assertTrue(result.startsWith("【HIS】"));
        assertTrue(result.contains("U: Q1"));
        assertTrue(result.contains("A: A1"));
        assertTrue(result.contains("U: Q2"));
        assertTrue(result.contains("A: A2"));
        assertTrue(result.endsWith("Current question"));
    }

    @Test
    void buildPromptWithHistory_returnsOriginalPrompt_whenHistoryEmpty() {
        String result = LlmService.buildPromptWithHistory("test prompt", List.of());
        assertEquals("test prompt", result);
    }

    @Test
    void buildPromptWithHistory_returnsOriginalPrompt_whenHistoryNull() {
        String result = LlmService.buildPromptWithHistory("test prompt", null);
        assertEquals("test prompt", result);
    }

    @Test
    void buildPromptWithHistory_handlesLargeHistory() {
        List<ChatMessage> history = List.of(
            new ChatMessage("user", "Long question 1 with lots of context", Instant.now()),
            new ChatMessage("assistant", "Long answer 1 explaining details", Instant.now()),
            new ChatMessage("user", "Follow up question", Instant.now()),
            new ChatMessage("assistant", "Follow up answer with more info", Instant.now()),
            new ChatMessage("user", "Third question", Instant.now()),
            new ChatMessage("assistant", "Third comprehensive answer", Instant.now())
        );

        String result = LlmService.buildPromptWithHistory("Final question", history);

        assertNotNull(result);
        assertTrue(result.contains("【HIS】"));
        assertTrue(result.contains("Final question"));
        // Should contain all history entries
        assertTrue(result.contains("Long question 1"));
        assertTrue(result.contains("Third comprehensive answer"));
    }

    @Test
    void buildPromptWithHistory_preservesPromptOrder() {
        List<ChatMessage> history = List.of(
            new ChatMessage("user", "First", Instant.now()),
            new ChatMessage("assistant", "Second", Instant.now())
        );

        String result = LlmService.buildPromptWithHistory("Third", history);

        int firstIdx = result.indexOf("First");
        int secondIdx = result.indexOf("Second");
        int thirdIdx = result.indexOf("Third");

        assertTrue(firstIdx < secondIdx);
        assertTrue(secondIdx < thirdIdx);
    }

    @Test
    void buildPromptWithHistory_handlesSpecialCharacters() {
        List<ChatMessage> history = List.of(
            new ChatMessage("user", "Question with <html> & special \"chars\"", Instant.now()),
            new ChatMessage("assistant", "Answer with 中文 and émojis 🎉", Instant.now())
        );

        String result = LlmService.buildPromptWithHistory("Normal prompt", history);

        assertTrue(result.contains("<html>"));
        assertTrue(result.contains("中文"));
        assertTrue(result.contains("🎉"));
    }
}
