package com.mrpot.agent.common.telemetry;

import java.time.Instant;
import java.util.Map;

public record RunLogEnvelope(
        String v,              // version, e.g. "1"
        String type,           // "run.start" | "run.rag_done" | "run.final" | "run.failed" | "run.cancelled"
        String runId,
        String traceId,
        String sessionId,
        String userId,
        String mode,           // GENERAL/OWNER 
        String model,
        Instant ts,
        Map<String, Object> data
) {}
