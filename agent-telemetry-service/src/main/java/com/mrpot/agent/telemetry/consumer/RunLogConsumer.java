package com.mrpot.agent.telemetry.consumer;

import com.mrpot.agent.common.telemetry.RunLogEnvelope;
import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import com.mrpot.agent.telemetry.repository.KnowledgeRunJpaRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RunLogConsumer {

    private final KnowledgeRunJpaRepository repo;

    @RabbitListener(queues = "mrpot.telemetry.q", ackMode = "MANUAL", concurrency = "2-4")
    public void onMessage(RunLogEnvelope env, Message msg, Channel ch) throws Exception {
        long tag = msg.getMessageProperties().getDeliveryTag();
        try {
            handle(env);
            ch.basicAck(tag, false);
        } catch (Exception ex) {
            // 失败：拒绝并进入 DLQ（不 requeue，避免无限循环）
            ch.basicNack(tag, false, false);
        }
    }

    private void handle(RunLogEnvelope e) {
        String type = e.type();
        switch (type) {
            case "run.start" -> onStart(e);
            case "run.rag_done" -> onRagDone(e);
            case "run.final" -> onFinal(e);
            case "run.failed" -> onFailed(e);
            case "run.cancelled" -> onCancelled(e);
            default -> { /* ignore */ }
        }
    }

    private void onStart(RunLogEnvelope e) {
        KnowledgeRunEntity ent = repo.findById(e.runId()).orElseGet(() -> {
            KnowledgeRunEntity n = new KnowledgeRunEntity();
            n.setId(e.runId());
            n.setCreatedAt(Instant.now());
            return n;
        });

        ent.setUpdatedAt(Instant.now());
        ent.setTraceId(e.traceId());
        ent.setSessionId(e.sessionId());
        ent.setUserId(e.userId());
        ent.setMode(e.mode());
        ent.setModel(e.model());
        ent.setQuestion(trunc((String) e.data().getOrDefault("question", ""), 3800));
        ent.setStatus("RUNNING");
        repo.save(ent);
    }

    private void onRagDone(RunLogEnvelope e) {
        repo.findById(e.runId()).ifPresent(ent -> {
            ent.setUpdatedAt(Instant.now());
            ent.setKbHitCount(toInt(e.data().get("kbHitCount"), 0));
            ent.setKbLatencyMs(toLong(e.data().get("kbLatencyMs"), 0L));

            Object ids = e.data().get("kbDocIds");
            if (ids instanceof List<?> list) {
                ent.setKbDocIds(String.join(",", list.stream().map(String::valueOf).toList()));
            } else {
                ent.setKbDocIds("");
            }
            repo.save(ent);
        });
    }

    private void onFinal(RunLogEnvelope e) {
        repo.findById(e.runId()).ifPresent(ent -> {
            ent.setUpdatedAt(Instant.now());
            ent.setAnswerFinal(trunc((String) e.data().getOrDefault("answerFinal", ""), 11000));
            ent.setTotalLatencyMs(toLong(e.data().get("totalLatencyMs"), 0L));
            ent.setStatus("DONE");
            repo.save(ent);
        });
    }

    private void onFailed(RunLogEnvelope e) {
        repo.findById(e.runId()).ifPresent(ent -> {
            ent.setUpdatedAt(Instant.now());
            ent.setStatus("FAILED");
            ent.setErrorCode(trunc(String.valueOf(e.data().getOrDefault("errorCode", "UNKNOWN")), 120));
            repo.save(ent);
        });
    }

    private void onCancelled(RunLogEnvelope e) {
        repo.findById(e.runId()).ifPresent(ent -> {
            ent.setUpdatedAt(Instant.now());
            ent.setStatus("CANCELLED");
            repo.save(ent);
        });
    }

    private static String trunc(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + " ...[truncated]";
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ignored) { return def; }
    }

    private static long toLong(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception ignored) { return def; }
    }
}
