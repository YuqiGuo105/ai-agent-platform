package com.mrpot.agent.service;

import com.mrpot.agent.service.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationHistoryServiceTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveListOperations<String, String> listOps;
    private ConversationHistoryService service;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(ReactiveStringRedisTemplate.class);
        listOps = Mockito.mock(ReactiveListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        service = new ConversationHistoryService(redisTemplate);
    }

    @Test
    void getRecentHistory_returnsEmptyList_whenSessionIdIsNull() {
        StepVerifier.create(service.getRecentHistory(null, 3))
            .expectNext(List.of())
            .verifyComplete();

        verifyNoInteractions(listOps);
    }

    @Test
    void getRecentHistory_returnsEmptyList_whenSessionIdIsBlank() {
        StepVerifier.create(service.getRecentHistory("  ", 3))
            .expectNext(List.of())
            .verifyComplete();

        verifyNoInteractions(listOps);
    }

    @Test
    void getRecentHistory_fetchesMessagesFromRedis() {
        String sessionId = "session123";
        String jsonMessage = "{\"role\":\"user\",\"content\":\"hello\",\"timestamp\":\"2026-01-01T00:00:00Z\"}";
        
        when(listOps.range(eq("chat:history:" + sessionId), anyLong(), anyLong()))
            .thenReturn(Flux.just(jsonMessage));

        StepVerifier.create(service.getRecentHistory(sessionId, 3))
            .assertNext(messages -> {
                assertEquals(1, messages.size());
                assertEquals("user", messages.get(0).role());
                assertEquals("hello", messages.get(0).content());
            })
            .verifyComplete();

        verify(listOps).range("chat:history:session123", -6, -1);
    }

    @Test
    void getRecentHistory_returnsEmptyListOnError() {
        String sessionId = "session123";
        
        when(listOps.range(anyString(), anyLong(), anyLong()))
            .thenReturn(Flux.error(new RuntimeException("Redis error")));

        StepVerifier.create(service.getRecentHistory(sessionId, 3))
            .expectNext(List.of())
            .verifyComplete();
    }

    @Test
    void getRecentHistory_skipsInvalidJsonMessages() {
        String sessionId = "session123";
        String validJson = "{\"role\":\"user\",\"content\":\"hello\",\"timestamp\":\"2026-01-01T00:00:00Z\"}";
        String invalidJson = "invalid json";
        
        when(listOps.range(anyString(), anyLong(), anyLong()))
            .thenReturn(Flux.just(invalidJson, validJson));

        StepVerifier.create(service.getRecentHistory(sessionId, 3))
            .assertNext(messages -> {
                assertEquals(1, messages.size());
                assertEquals("user", messages.get(0).role());
            })
            .verifyComplete();
    }

    @Test
    void saveMessage_doesNothingWhenSessionIdIsNull() {
        StepVerifier.create(service.saveMessage(null, "user", "hello"))
            .verifyComplete();

        verifyNoInteractions(listOps);
    }

    @Test
    void saveMessage_savesMessageToRedis() {
        String sessionId = "session123";
        when(listOps.rightPush(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(listOps.trim(anyString(), anyLong(), anyLong())).thenReturn(Mono.just(true));

        StepVerifier.create(service.saveMessage(sessionId, "user", "test message"))
            .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(listOps).rightPush(keyCaptor.capture(), valueCaptor.capture());
        assertEquals("chat:history:session123", keyCaptor.getValue());
        assertTrue(valueCaptor.getValue().contains("\"role\":\"user\""));
        assertTrue(valueCaptor.getValue().contains("\"content\":\"test message\""));
    }

    @Test
    void saveMessage_setsTtlOnKey() {
        String sessionId = "session123";
        when(listOps.rightPush(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(listOps.trim(anyString(), anyLong(), anyLong())).thenReturn(Mono.just(true));

        service.saveMessage(sessionId, "user", "test").block();

        verify(redisTemplate).expire(eq("chat:history:session123"), eq(Duration.ofDays(7)));
    }

    @Test
    void saveConversationPair_savesBothMessages() {
        String sessionId = "session123";
        when(listOps.rightPush(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(listOps.trim(anyString(), anyLong(), anyLong())).thenReturn(Mono.just(true));

        StepVerifier.create(service.saveConversationPair(sessionId, "question", "answer"))
            .verifyComplete();

        verify(listOps, times(2)).rightPush(eq("chat:history:session123"), anyString());
    }

    @Test
    void clearHistory_deletesRedisKey() {
        String sessionId = "session123";
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.clearHistory(sessionId))
            .expectNext(true)
            .verifyComplete();

        verify(redisTemplate).delete("chat:history:session123");
    }

    @Test
    void clearHistory_returnsFalseWhenKeyNotExists() {
        String sessionId = "session123";
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(service.clearHistory(sessionId))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void clearHistory_returnsFalseWhenSessionIdIsNull() {
        StepVerifier.create(service.clearHistory(null))
            .expectNext(false)
            .verifyComplete();

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void getHistorySize_returnsListSize() {
        String sessionId = "session123";
        when(listOps.size(anyString())).thenReturn(Mono.just(5L));

        StepVerifier.create(service.getHistorySize(sessionId))
            .expectNext(5L)
            .verifyComplete();

        verify(listOps).size("chat:history:session123");
    }

    @Test
    void getHistorySize_returnsZeroWhenSessionIdIsNull() {
        StepVerifier.create(service.getHistorySize(null))
            .expectNext(0L)
            .verifyComplete();

        verifyNoInteractions(listOps);
    }
}
