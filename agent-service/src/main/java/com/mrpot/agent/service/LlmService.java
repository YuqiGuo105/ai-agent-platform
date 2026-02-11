package com.mrpot.agent.service;

import com.mrpot.agent.service.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for LLM interactions using Spring AI ChatClient.
 * Integrates with DeepSeek as the primary LLM provider.
 */
@Slf4j
@Service
public class LlmService {

    private final ChatClient chatClient;

    public LlmService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Stream LLM response for a given prompt.
     *
     * @param prompt the prompt text
     * @return Flux of streaming tokens
     */
    public Flux<String> streamResponse(String prompt) {
        return streamResponse(prompt, List.of());
    }

    /**
     * Stream LLM response with conversation history context.
     *
     * @param prompt           the current prompt/question
     * @param conversationHistory list of previous chat messages
     * @return Flux of streaming tokens
     */
    public Flux<String> streamResponse(String prompt, List<ChatMessage> conversationHistory) {
        log.debug("Streaming LLM response: promptLength={}, historySize={}",
            prompt.length(), conversationHistory.size());

        // Build the full prompt with history
        String fullPrompt = buildPromptWithHistory(prompt, conversationHistory);

        return chatClient.prompt()
            .user(fullPrompt)
            .stream()
            .content()
            .doOnSubscribe(s -> log.debug("Started LLM streaming"))
            .doOnComplete(() -> log.debug("LLM streaming completed"))
            .doOnError(e -> log.error("LLM streaming error: {}", e.getMessage(), e));
    }

    /**
     * Build the complete prompt including conversation history.
     *
     * @param currentPrompt      the current prompt (includes file context, RAG context, question)
     * @param conversationHistory previous conversation messages
     * @return formatted prompt string
     */
    public static String buildPromptWithHistory(String currentPrompt, List<ChatMessage> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return currentPrompt;
        }

        StringBuilder promptBuilder = new StringBuilder();

        // Add conversation history section
        promptBuilder.append("【Previous conversation】\n");
        for (ChatMessage message : conversationHistory) {
            String roleLabel = "user".equalsIgnoreCase(message.role()) ? "User" : "Assistant";
            promptBuilder.append(roleLabel).append(": ").append(message.content()).append("\n");
        }
        promptBuilder.append("\n");

        // Add the current prompt content
        promptBuilder.append(currentPrompt);

        return promptBuilder.toString();
    }

    /**
     * Generate a non-streaming response.
     * Useful for short responses or when streaming is not needed.
     *
     * @param prompt the prompt text
     * @return the complete response
     */
    public String generateResponse(String prompt) {
        log.debug("Generating non-streaming LLM response: promptLength={}", prompt.length());

        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }
}
