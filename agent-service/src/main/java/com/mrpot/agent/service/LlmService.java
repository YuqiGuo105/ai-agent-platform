package com.mrpot.agent.service;

import com.mrpot.agent.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Service for LLM interactions using Spring AI ChatClient.
 * Integrates with DeepSeek as the primary LLM provider.
 */
@Slf4j
@Service
public class LlmService {

    /**
     * System prompt for Mr Pot AI assistant.
     * Works across both FAST and DEEP execution modes.
     */
    private static final String SYSTEM_PROMPT = """
        You are Mr Pot, Yuqi Guo's AI assistant. Friendly, slightly playful.
        MUST reply in the same language as【Q】. Never switch language. Never echo markers like【Q】【QA】【KB】in your response.
        Use【QA】as primary answer,【KB】【FILE】【HIS】as evidence. Don't fabricate facts.
        Format: GFM markdown, math ($/$$/\\ce{}), code blocks with lang tags.
        """;

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
            .system(SYSTEM_PROMPT)
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
        promptBuilder.append("【HIS】\n");
        for (ChatMessage message : conversationHistory) {
            String roleLabel = "user".equalsIgnoreCase(message.role()) ? "U" : "A";
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
            .system(SYSTEM_PROMPT)
            .user(prompt)
            .call()
            .content();
    }
}
