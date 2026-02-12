package com.mrpot.agent.service;

import com.mrpot.agent.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for LLM interactions using Spring AI ChatClient.
 * Integrates with DeepSeek as the primary LLM provider.
 */
@Slf4j
@Service
public class LlmService {

    /**
     * Base identity and rules - shared across all modes.
     */
    private static final String BASE_PROMPT = """
        You are Mr Pot, Yuqi Guo's AI assistant.
        MUST reply in the same language as【Q】. Never switch language. Never echo markers like【Q】【QA】【KB】.
        Use【QA】as primary answer,【KB】【FILE】【HIS】as evidence. Don't fabricate facts.
        Format: GFM markdown, math ($/$$/\\ce{}), code blocks with lang tags.
        """;

    /**
     * Mode-specific style hints (keep as short keywords/phrases to save tokens).
     * Add a new mode: MODE_TONES.put("NAME", "keywords...");
     */
    private static final Map<String, String> MODE_TONES = new ConcurrentHashMap<>(Map.of(
            "DEFAULT", "friendly, concise",
            "FAST",    "humorous, very concise, witty",
            "DEEP",    "structured, thorough (no fluff)",
            "CONCISE", "minimal words, direct"
    ));

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
        return streamResponse(prompt, conversationHistory, null);
    }

    /**
     * Stream LLM response with conversation history and execution mode.
     *
     * @param prompt              the current prompt/question
     * @param conversationHistory list of previous chat messages
     * @param executionMode       execution mode (FAST, DEEP, CONCISE, etc.)
     * @return Flux of streaming tokens
     */
    public Flux<String> streamResponse(String prompt, List<ChatMessage> conversationHistory, String executionMode) {
        log.debug("Streaming LLM response: promptLength={}, historySize={}, mode={}",
            prompt.length(), conversationHistory.size(), executionMode);

        // Build the full prompt with history
        String fullPrompt = buildPromptWithHistory(prompt, conversationHistory);

        // Get system prompt for the mode (fallback to DEFAULT if not found)
        String systemPrompt = getSystemPromptForMode(executionMode);

        return chatClient.prompt()
            .system(systemPrompt)
            .user(fullPrompt)
            .stream()
            .content()
            .doOnSubscribe(s -> log.debug("Started LLM streaming (mode={})", executionMode))
            .doOnComplete(() -> log.debug("LLM streaming completed"))
            .doOnError(e -> log.error("LLM streaming error: {}", e.getMessage(), e));
    }

    /**
     * Get the full system prompt for a given execution mode.
     * Combines BASE_PROMPT with mode-specific tone.
     *
     * @param mode the execution mode
     * @return the complete system prompt
     */
    public static String getSystemPromptForMode(String mode) {
        String tone = (mode == null || mode.isBlank()) 
            ? MODE_TONES.get("DEFAULT")
            : MODE_TONES.getOrDefault(mode.toUpperCase(), MODE_TONES.get("DEFAULT"));
        return BASE_PROMPT + "\nTone: " + tone;
    }

    /**
     * Register a custom tone for a new mode.
     * Useful for runtime mode registration or testing.
     *
     * @param mode the mode name (will be uppercased)
     * @param tone the tone description (e.g., "Formal and academic")
     */
    public static void registerModeTone(String mode, String tone) {
        MODE_TONES.put(mode.toUpperCase(), tone);
        log.info("Registered tone for mode: {}", mode.toUpperCase());
    }

    /**
     * Get all available execution modes.
     *
     * @return set of available mode names
     */
    public static java.util.Set<String> getAvailableModes() {
        return MODE_TONES.keySet();
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
        return generateResponse(prompt, null);
    }

    /**
     * Generate a non-streaming response with execution mode.
     *
     * @param prompt        the prompt text
     * @param executionMode execution mode (FAST, DEEP, CONCISE, etc.)
     * @return the complete response
     */
    public String generateResponse(String prompt, String executionMode) {
        log.debug("Generating non-streaming LLM response: promptLength={}, mode={}", prompt.length(), executionMode);

        String systemPrompt = getSystemPromptForMode(executionMode);

        return chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .call()
            .content();
    }
}
