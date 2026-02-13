package com.mrpot.agent.knowledge.service;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.todo.TodoItem;
import com.mrpot.agent.common.todo.TodoStatus;
import com.mrpot.agent.knowledge.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository repository;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong seqCounter = new AtomicLong(0);

    public List<TodoItem> getAllTodos() {
        return repository.findAll();
    }

    public TodoItem getTodoById(Long id) {
        return repository.findById(id);
    }

    public TodoItem createTodo(String title, String description) {
        log.info("Creating todo: title='{}'", title);
        TodoItem created = repository.create(title, description);
        broadcastSse(StageNames.TODO_UPDATE, "Todo created", created);
        return created;
    }

    public TodoItem updateTodo(Long id, String title, String description, TodoStatus status) {
        log.info("Updating todo id={}, status={}", id, status);
        TodoItem updated = repository.update(id, title, description, status);
        if (updated == null) return null;

        String stage = (updated.status() == TodoStatus.COMPLETED)
            ? StageNames.TODO_COMPLETE
            : StageNames.TODO_UPDATE;
        String message = (updated.status() == TodoStatus.COMPLETED)
            ? "Todo completed"
            : "Todo updated";

        broadcastSse(stage, message, updated);
        return updated;
    }

    public boolean deleteTodo(Long id) {
        log.info("Deleting todo id={}", id);
        boolean deleted = repository.deleteById(id);
        if (deleted) {
            broadcastSse(StageNames.TODO_UPDATE, "Todo deleted", id);
        }
        return deleted;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        List<TodoItem> current = repository.findAll();
        SseEnvelope initial = SseEnvelope.builder()
            .stage(StageNames.TODO_LIST)
            .message("Current todo list")
            .payload(current)
            .seq(seqCounter.incrementAndGet())
            .ts(System.currentTimeMillis())
            .build();
        try {
            emitter.send(SseEmitter.event().name("message").data(initial));
        } catch (IOException e) {
            log.warn("Failed to send initial todo list", e);
            emitters.remove(emitter);
        }

        return emitter;
    }

    private void broadcastSse(String stage, String message, Object payload) {
        SseEnvelope envelope = SseEnvelope.builder()
            .stage(stage)
            .message(message)
            .payload(payload)
            .seq(seqCounter.incrementAndGet())
            .ts(System.currentTimeMillis())
            .build();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("message").data(envelope));
            } catch (IOException e) {
                log.debug("Removing dead SSE emitter");
                emitters.remove(emitter);
            }
        }
    }
}
