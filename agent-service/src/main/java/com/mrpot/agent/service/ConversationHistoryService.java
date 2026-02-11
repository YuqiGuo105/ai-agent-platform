package com.mrpot.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Service for managing conversation history in Redis.
 * Uses Redis List data structure to maintain message order.
 * Messages are stored with a TTL to prevent unbounded growth.
 */
@Slf4j
@Service
public class ConversationHistoryService {

    private static final String KEY_PREFIX = "chat:history:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final int DEFAULT_HISTORY_LIMIT = 3;
    private static final int MAX_HISTORY_SIZE = 50;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ConversationHistoryService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // For Java 8 date/time support
    }

    /**
     * Get recent conversation history for a session.
     *
     * @param sessionId the session ID
     * @param limit     maximum number of messages to retrieve (pairs of user+assistant)
     * @return Mono containing list of recent chat messages
     */
    public Mono<List<ChatMessage>> getRecentHistory(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.just(Collections.emptyList());
        }

        String key = buildKey(sessionId);
        // Limit is for message pairs, so we need to fetch limit * 2 individual messages
        int messageCount = Math.min(limit * 2, MAX_HISTORY_SIZE);

        log.debug("Fetching conversation history: sessionId={}, limit={}", sessionId, limit);

        return redisTemplate.opsForList()
            .range(key, -messageCount, -1)  // Get last N messages
            .flatMap(this::deserializeMessage)
            .collectList()
            .doOnSuccess(messages -> log.debug(
                "Retrieved {} messages from history for sessionId={}",
                messages.size(), sessionId))
            .onErrorResume(e -> {
                log.error("Error fetching conversation history for sessionId={}: {}",
                    sessionId, e.getMessage());
                return Mono.just(Collections.emptyList());
            });
    }

    /**
     * Get recent conversation history with default limit.
     *
     * @param sessionId the session ID
     * @return Mono containing list of recent chat messages (default 3 pairs)
     */
    public Mono<List<ChatMessage>> getRecentHistory(String sessionId) {
        return getRecentHistory(sessionId, DEFAULT_HISTORY_LIMIT);
    }

    /**
     * Save a message to conversation history.
     *
     * @param sessionId the session ID
     * @param role      message role ("user" or "assistant")
     * @param content   message content
     * @return Mono<Void> completing when save is done
     */
    public Mono<Void> saveMessage(String sessionId, String role, String content) {
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.empty();
        }

        ChatMessage message = new ChatMessage(role, content, Instant.now());
        return saveMessage(sessionId, message);
    }

    /**
     * Save a ChatMessage to conversation history.
     *
     * @param sessionId the session ID
     * @param message   the chat message
     * @return Mono<Void> completing when save is done
     */
    public Mono<Void> saveMessage(String sessionId, ChatMessage message) {
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.empty();
        }

        String key = buildKey(sessionId);

        return serializeMessage(message)
            .flatMap(serialized -> 
                redisTemplate.opsForList()
                    .rightPush(key, serialized)
                    .then(redisTemplate.expire(key, DEFAULT_TTL))
                    .then(trimHistory(key))
            )
            .doOnSuccess(v -> log.debug(
                "Saved message to history: sessionId={}, role={}",
                sessionId, message.role()))
            .onErrorResume(e -> {
                log.error("Error saving message to history for sessionId={}: {}",
                    sessionId, e.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Save both user question and assistant answer as a conversation pair.
     *
     * @param sessionId the session ID
     * @param question  the user question
     * @param answer    the assistant answer
     * @return Mono<Void> completing when both messages are saved
     */
    public Mono<Void> saveConversationPair(String sessionId, String question, String answer) {
        return saveMessage(sessionId, "user", question)
            .then(saveMessage(sessionId, "assistant", answer));
    }

    /**
     * Clear conversation history for a session.
     *
     * @param sessionId the session ID
     * @return Mono<Boolean> true if key was deleted
     */
    public Mono<Boolean> clearHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.just(false);
        }

        String key = buildKey(sessionId);
        return redisTemplate.delete(key)
            .map(deleted -> deleted > 0)
            .doOnSuccess(deleted -> {
                if (deleted) {
                    log.info("Cleared conversation history for sessionId={}", sessionId);
                }
            });
    }

    /**
     * Get the count of messages in history.
     *
     * @param sessionId the session ID
     * @return Mono containing message count
     */
    public Mono<Long> getHistorySize(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.just(0L);
        }

        String key = buildKey(sessionId);
        return redisTemplate.opsForList().size(key);
    }

    /**
     * Trim history to prevent unbounded growth.
     */
    private Mono<Void> trimHistory(String key) {
        // Keep only the last MAX_HISTORY_SIZE messages
        return redisTemplate.opsForList()
            .trim(key, -MAX_HISTORY_SIZE, -1)
            .then();
    }

    /**
     * Build Redis key for a session.
     */
    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    /**
     * Serialize ChatMessage to JSON string.
     */
    private Mono<String> serializeMessage(ChatMessage message) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(message));
    }

    /**
     * Deserialize JSON string to ChatMessage.
     */
    private Mono<ChatMessage> deserializeMessage(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, ChatMessage.class))
            .onErrorResume(e -> {
                log.warn("Failed to deserialize message: {}", e.getMessage());
                return Mono.empty();
            });
    }
}
