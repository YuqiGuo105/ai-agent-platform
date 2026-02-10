-- =============================================
-- Test Data for Telemetry Service Integration Tests
-- =============================================
-- This file contains dummy data for all telemetry tables:
-- - knowledge_run
-- - knowledge_tool_call
-- - telemetry_dlq_message
-- - es_outbox
-- - knowledge_run_event
--
-- Data Summary:
-- - 5 knowledge_run records (sessions: session-abc-123, session-def-456, session-ghi-789)
-- - 9 knowledge_tool_call records across the runs
-- - 8 telemetry_dlq_message records (PENDING: 4, REQUEUED: 2, IGNORED: 2)
-- - 6 es_outbox records (PENDING: 3, SENT: 2, FAILED: 1)
-- - 8 knowledge_run_event records for idempotency
-- =============================================

-- =============================================
-- Knowledge Run Test Data
-- =============================================

INSERT INTO knowledge_run (id, trace_id, session_id, user_id, mode, model, question, answer_final, status, 
    kb_hit_count, kb_doc_ids, kb_latency_ms, total_latency_ms, error_code, created_at, updated_at) VALUES
('run-001-uuid', 'trace-xyz-001', 'session-abc-123', 'user-001', 'GENERAL', 'deepseek', 
    'What is machine learning?', 'Machine learning is a subset of AI that enables systems to learn from data...', 
    'DONE', 3, 'doc-1,doc-2,doc-3', 450, 1500, NULL, '2026-02-09 10:00:00', '2026-02-09 10:00:15');

INSERT INTO knowledge_run (id, trace_id, session_id, user_id, mode, model, question, answer_final, status, 
    kb_hit_count, kb_doc_ids, kb_latency_ms, total_latency_ms, error_code, created_at, updated_at) VALUES
('run-002-uuid', 'trace-xyz-002', 'session-abc-123', 'user-001', 'RAG', 'gpt-4o', 
    'How does vector similarity search work?', 'Vector similarity search uses mathematical distance functions...', 
    'DONE', 5, 'doc-4,doc-5,doc-6,doc-7,doc-8', 320, 2100, NULL, '2026-02-09 10:05:00', '2026-02-09 10:05:21');

INSERT INTO knowledge_run (id, trace_id, session_id, user_id, mode, model, question, answer_final, status, 
    kb_hit_count, kb_doc_ids, kb_latency_ms, total_latency_ms, error_code, created_at, updated_at) VALUES
('run-003-uuid', 'trace-xyz-003', 'session-def-456', 'user-002', 'GENERAL', 'claude-3', 
    'Explain RAG architecture', NULL, 'RUNNING', 0, NULL, NULL, NULL, NULL, '2026-02-09 10:10:00', '2026-02-09 10:10:00');

INSERT INTO knowledge_run (id, trace_id, session_id, user_id, mode, model, question, answer_final, status, 
    kb_hit_count, kb_doc_ids, kb_latency_ms, total_latency_ms, error_code, created_at, updated_at) VALUES
('run-004-uuid', 'trace-xyz-004', 'session-def-456', 'user-002', 'DEBUG', 'deepseek', 
    'Why is my query slow?', NULL, 'FAILED', 1, 'doc-9', 5000, 5500, 'TIMEOUT', '2026-02-09 10:15:00', '2026-02-09 10:15:55');

INSERT INTO knowledge_run (id, trace_id, session_id, user_id, mode, model, question, answer_final, status, 
    kb_hit_count, kb_doc_ids, kb_latency_ms, total_latency_ms, error_code, created_at, updated_at) VALUES
('run-005-uuid', 'trace-xyz-005', 'session-ghi-789', 'user-003', 'GENERAL', 'gpt-4o', 
    'What is Spring Boot?', 'Spring Boot is a framework that simplifies Java development...', 
    'DONE', 2, 'doc-10,doc-11', 280, 1200, NULL, '2026-02-09 10:20:00', '2026-02-09 10:20:12');

-- =============================================
-- Knowledge Tool Call Test Data
-- =============================================

-- Tool calls for run-001
INSERT INTO knowledge_tool_call (id, run_id, tool_name, attempt, ok, duration_ms, args_digest, args_preview, 
    args_size, result_digest, result_preview, result_size, cache_hit, ttl_hint_seconds, freshness, 
    error_code, error_msg, retryable, key_info_json, called_at, created_at) VALUES
('tc-001', 'run-001-uuid', 'kb_search', 1, true, 250, 'sha256-args-001', '{"query":"machine learning"}', 
    45, 'sha256-result-001', '{"results":[{"id":"doc-1","score":0.92},...]}', 1024, false, 3600, 'FRESH', 
    NULL, NULL, NULL, '{"docs_found":3}', '2026-02-09 10:00:02', '2026-02-09 10:00:02');

INSERT INTO knowledge_tool_call (id, run_id, tool_name, attempt, ok, duration_ms, args_digest, args_preview, 
    args_size, result_digest, result_preview, result_size, cache_hit, ttl_hint_seconds, freshness, 
    error_code, error_msg, retryable, key_info_json, called_at, created_at) VALUES
('tc-002', 'run-001-uuid', 'llm_generate', 1, true, 1200, 'sha256-args-002', '{"context":"...", "question":"..."}', 
    2048, 'sha256-result-002', '{"response":"Machine learning is..."}', 512, false, NULL, NULL, 
    NULL, NULL, NULL, '{"tokens":150}', '2026-02-09 10:00:03', '2026-02-09 10:00:03');

-- Tool calls for run-002
INSERT INTO knowledge_tool_call (id, run_id, tool_name, attempt, ok, duration_ms, args_digest, args_preview, 
    args_size, result_digest, result_preview, result_size, cache_hit, ttl_hint_seconds, freshness, 
    error_code, error_msg, retryable, key_info_json, called_at, created_at) VALUES
('tc-003', 'run-002-uuid', 'kb_search', 1, true, 180, 'sha256-args-003', '{"query":"vector similarity"}', 
    38, 'sha256-result-003', '{"results":[{"id":"doc-4","score":0.95},...]}', 2048, true, 3600, 'FRESH', 
    NULL, NULL, NULL, '{"docs_found":5}', '2026-02-09 10:05:02', '2026-02-09 10:05:02');

INSERT INTO knowledge_tool_call (id, run_id, tool_name, attempt, ok, duration_ms, args_digest, args_preview, 
    args_size, result_digest, result_preview, result_size, cache_hit, ttl_hint_seconds, freshness, 
    error_code, error_msg, retryable, key_info_json, called_at, created_at) VALUES
('tc-004', 'run-002-uuid', 'web_search', 1, true, 450, 'sha256-args-004', '{"query":"HNSW algorithm"}', 
    32, 'sha256-result-004', '{"results":[{"url":"https://...","snippet":"..."}]}', 4096, false, 1800, 'FRESH', 
    NULL, NULL, NULL, '{"results_count":10}', '2026-02-09 10:05:05', '2026-02-09 10:05:05');

INSERT INTO knowledge_tool_call (id, run_id, tool_name, attempt, ok, duration_ms, args_digest, args_preview, 
    args_size, result_digest, result_preview, result_size, cache_hit, ttl_hint_seconds, freshness, 
    error_code, error_msg, retryable, key_info_json, called_at, created_at) VALUES
('tc-005', 'run-002-uuid', 'llm_generate', 1, true, 1400, 'sha256-args-005', '{"context":"...", "question":"..."}', 
    4096, 'sha256-result-005', '{"response":"Vector similarity search..."}', 1024, false, NULL, NULL, 
    NULL, NULL, NULL, '{"tokens":280}', '2026-02-09 10:05:08', '2026-02-09 10:05:08');

-- Tool calls for run-004 (failed run)
INSERT INTO knowledge_tool_call (id, run_id, tool_name, attempt, ok, duration_ms, args_digest, args_preview, 
    args_size, result_digest, result_preview, result_size, cache_hit, ttl_hint_seconds, freshness, 
    error_code, error_msg, retryable, key_info_json, called_at, created_at) VALUES
('tc-006', 'run-004-uuid', 'kb_search', 1, false, 5000, 'sha256-args-006', '{"query":"slow query debug"}', 
    42, NULL, NULL, NULL, false, NULL, 'MISS', 'TIMEOUT', 'Database query timeout after 5000ms', true, 
    NULL, '2026-02-09 10:15:05', '2026-02-09 10:15:05');

INSERT INTO knowledge_tool_call (id, run_id, tool_name, attempt, ok, duration_ms, args_digest, args_preview, 
    args_size, result_digest, result_preview, result_size, cache_hit, ttl_hint_seconds, freshness, 
    error_code, error_msg, retryable, key_info_json, called_at, created_at) VALUES
('tc-007', 'run-004-uuid', 'kb_search', 2, false, 5000, 'sha256-args-006', '{"query":"slow query debug"}', 
    42, NULL, NULL, NULL, false, NULL, 'MISS', 'TIMEOUT', 'Database query timeout after 5000ms (retry)', true, 
    NULL, '2026-02-09 10:15:15', '2026-02-09 10:15:15');

-- Tool calls for run-005
INSERT INTO knowledge_tool_call (id, run_id, tool_name, attempt, ok, duration_ms, args_digest, args_preview, 
    args_size, result_digest, result_preview, result_size, cache_hit, ttl_hint_seconds, freshness, 
    error_code, error_msg, retryable, key_info_json, called_at, created_at) VALUES
('tc-008', 'run-005-uuid', 'kb_search', 1, true, 200, 'sha256-args-008', '{"query":"Spring Boot"}', 
    28, 'sha256-result-008', '{"results":[{"id":"doc-10","score":0.89},...]}', 768, false, 3600, 'FRESH', 
    NULL, NULL, NULL, '{"docs_found":2}', '2026-02-09 10:20:02', '2026-02-09 10:20:02');

INSERT INTO knowledge_tool_call (id, run_id, tool_name, attempt, ok, duration_ms, args_digest, args_preview, 
    args_size, result_digest, result_preview, result_size, cache_hit, ttl_hint_seconds, freshness, 
    error_code, error_msg, retryable, key_info_json, called_at, created_at) VALUES
('tc-009', 'run-005-uuid', 'llm_generate', 1, true, 950, 'sha256-args-009', '{"context":"...", "question":"..."}', 
    1536, 'sha256-result-009', '{"response":"Spring Boot is..."}', 384, false, NULL, NULL, 
    NULL, NULL, NULL, '{"tokens":95}', '2026-02-09 10:20:04', '2026-02-09 10:20:04');

-- =============================================
-- Telemetry DLQ Message Test Data
-- Status: PENDING (4), REQUEUED (2), IGNORED (2)
-- =============================================

INSERT INTO telemetry_dlq_message (id, received_at, exchange, routing_key, headers, payload_json, payload_text,
    error_type, error_msg, run_id, trace_id, session_id, status, requeue_count, last_requeue_at) VALUES
(101, '2026-02-09 09:00:00', 'mrpot.telemetry.x', 'telemetry.run.start', 
    '{"x-death":[{"count":1,"reason":"rejected"}]}', 
    '{"type":"run.start","runId":"run-dlq-001","traceId":"trace-dlq-001","sessionId":"session-dlq-001","timestamp":"2026-02-09T09:00:00Z"}', 
    NULL, 'JsonParseException', 'Unexpected character at position 45', 
    'run-dlq-001', 'trace-dlq-001', 'session-dlq-001', 'PENDING', 0, NULL);

INSERT INTO telemetry_dlq_message (id, received_at, exchange, routing_key, headers, payload_json, payload_text,
    error_type, error_msg, run_id, trace_id, session_id, status, requeue_count, last_requeue_at) VALUES
(102, '2026-02-09 09:05:00', 'mrpot.telemetry.x', 'telemetry.run.final', 
    '{"x-death":[{"count":2,"reason":"rejected"}]}', 
    '{"type":"run.final","runId":"run-dlq-002","traceId":"trace-dlq-002","sessionId":"session-dlq-002"}', 
    NULL, 'ConstraintViolation', 'Duplicate key value violates unique constraint', 
    'run-dlq-002', 'trace-dlq-002', 'session-dlq-002', 'PENDING', 0, NULL);

INSERT INTO telemetry_dlq_message (id, received_at, exchange, routing_key, headers, payload_json, payload_text,
    error_type, error_msg, run_id, trace_id, session_id, status, requeue_count, last_requeue_at) VALUES
(103, '2026-02-09 09:10:00', 'mrpot.telemetry.x', 'telemetry.tool.end', 
    '{"x-death":[{"count":1,"reason":"expired"}]}', 
    '{"type":"tool.end","runId":"run-dlq-003","toolCallId":"tc-dlq-003","traceId":"trace-dlq-003"}', 
    NULL, 'TransientDataAccessException', 'Connection pool exhausted', 
    'run-dlq-003', 'trace-dlq-003', NULL, 'PENDING', 0, NULL);

INSERT INTO telemetry_dlq_message (id, received_at, exchange, routing_key, headers, payload_json, payload_text,
    error_type, error_msg, run_id, trace_id, session_id, status, requeue_count, last_requeue_at) VALUES
(104, '2026-02-09 09:15:00', 'mrpot.telemetry.x', 'telemetry.run.failed', 
    '{"x-death":[{"count":3,"reason":"rejected"}]}', 
    '{"type":"run.failed","runId":"run-dlq-004","errorCode":"INTERNAL_ERROR","traceId":"trace-dlq-004"}', 
    NULL, 'NullPointerException', 'Cannot invoke method on null object', 
    'run-dlq-004', 'trace-dlq-004', NULL, 'REQUEUED', 2, '2026-02-09 09:30:00');

INSERT INTO telemetry_dlq_message (id, received_at, exchange, routing_key, headers, payload_json, payload_text,
    error_type, error_msg, run_id, trace_id, session_id, status, requeue_count, last_requeue_at) VALUES
(105, '2026-02-09 09:20:00', 'mrpot.telemetry.x', 'telemetry.tool.error', 
    '{"x-death":[{"count":1,"reason":"rejected"}]}', 
    NULL, 'Invalid non-JSON message: <corrupted binary data>', 
    'MessageConversionException', 'Cannot convert message to JSON', 
    NULL, NULL, NULL, 'IGNORED', 0, NULL);

INSERT INTO telemetry_dlq_message (id, received_at, exchange, routing_key, headers, payload_json, payload_text,
    error_type, error_msg, run_id, trace_id, session_id, status, requeue_count, last_requeue_at) VALUES
(106, '2026-02-09 09:25:00', 'mrpot.telemetry.x', 'telemetry.run.start', 
    '{"x-death":[{"count":1,"reason":"rejected"}]}', 
    '{"type":"run.start","runId":"run-dlq-006","traceId":"trace-dlq-006","sessionId":"session-dlq-006"}', 
    NULL, 'DataIntegrityViolation', 'Value too long for column "question"', 
    'run-dlq-006', 'trace-dlq-006', 'session-dlq-006', 'PENDING', 0, NULL);

INSERT INTO telemetry_dlq_message (id, received_at, exchange, routing_key, headers, payload_json, payload_text,
    error_type, error_msg, run_id, trace_id, session_id, status, requeue_count, last_requeue_at) VALUES
(107, '2026-02-09 09:30:00', 'mrpot.telemetry.x', 'telemetry.run.final', 
    '{"x-death":[{"count":5,"reason":"rejected"}]}', 
    '{"type":"run.final","runId":"run-dlq-007","answer":"completed","traceId":"trace-dlq-007"}', 
    NULL, 'OptimisticLockingFailure', 'Row was updated by another transaction', 
    'run-dlq-007', 'trace-dlq-007', NULL, 'REQUEUED', 3, '2026-02-09 10:00:00');

INSERT INTO telemetry_dlq_message (id, received_at, exchange, routing_key, headers, payload_json, payload_text,
    error_type, error_msg, run_id, trace_id, session_id, status, requeue_count, last_requeue_at) VALUES
(108, '2026-02-09 09:35:00', '', '', 
    '{}', 
    '{"type":"unknown","data":"test"}', 
    NULL, 'UnknownMessageType', 'Cannot process message type: unknown', 
    NULL, NULL, NULL, 'IGNORED', 0, NULL);

-- =============================================
-- ES Outbox Test Data
-- Status: PENDING (3), SENT (2), FAILED (1)
-- =============================================

INSERT INTO es_outbox (id, run_id, index_name, doc_id, doc_json, status, retry_count, next_retry_at, last_error, created_at, updated_at) VALUES
(101, 'run-001-uuid', 'mrpot_runs', 'run-001-uuid', 
    '{"runId":"run-001-uuid","status":"DONE","model":"deepseek"}', 
    'PENDING', 0, '2026-02-09 10:00:15', NULL, '2026-02-09 10:00:15', '2026-02-09 10:00:15');

INSERT INTO es_outbox (id, run_id, index_name, doc_id, doc_json, status, retry_count, next_retry_at, last_error, created_at, updated_at) VALUES
(102, 'run-001-uuid', 'mrpot_tool_calls', 'tc-001', 
    '{"toolCallId":"tc-001","runId":"run-001-uuid","toolName":"kb_search","ok":true}', 
    'PENDING', 0, '2026-02-09 10:00:02', NULL, '2026-02-09 10:00:02', '2026-02-09 10:00:02');

INSERT INTO es_outbox (id, run_id, index_name, doc_id, doc_json, status, retry_count, next_retry_at, last_error, created_at, updated_at) VALUES
(103, 'run-002-uuid', 'mrpot_runs', 'run-002-uuid', 
    '{"runId":"run-002-uuid","status":"DONE","model":"gpt-4o"}', 
    'SENT', 0, '2026-02-09 10:05:22', NULL, '2026-02-09 10:05:21', '2026-02-09 10:05:22');

INSERT INTO es_outbox (id, run_id, index_name, doc_id, doc_json, status, retry_count, next_retry_at, last_error, created_at, updated_at) VALUES
(104, 'run-003-uuid', 'mrpot_runs', 'run-003-uuid', 
    '{"runId":"run-003-uuid","status":"RUNNING","model":"claude-3"}', 
    'PENDING', 0, '2026-02-09 10:10:00', NULL, '2026-02-09 10:10:00', '2026-02-09 10:10:00');

INSERT INTO es_outbox (id, run_id, index_name, doc_id, doc_json, status, retry_count, next_retry_at, last_error, created_at, updated_at) VALUES
(105, 'run-004-uuid', 'mrpot_runs', 'run-004-uuid', 
    '{"runId":"run-004-uuid","status":"FAILED","errorCode":"TIMEOUT"}', 
    'FAILED', 5, '2026-02-09 10:30:00', 'Connection refused: ES cluster unavailable after 5 retries', 
    '2026-02-09 10:15:55', '2026-02-09 10:30:00');

INSERT INTO es_outbox (id, run_id, index_name, doc_id, doc_json, status, retry_count, next_retry_at, last_error, created_at, updated_at) VALUES
(106, 'run-005-uuid', 'mrpot_runs', 'run-005-uuid', 
    '{"runId":"run-005-uuid","status":"DONE","model":"gpt-4o"}', 
    'SENT', 0, '2026-02-09 10:20:13', NULL, '2026-02-09 10:20:12', '2026-02-09 10:20:13');

-- =============================================
-- Knowledge Run Event Test Data (Idempotency)
-- =============================================

INSERT INTO knowledge_run_event (event_id, run_id, event_type, processed_at) VALUES
('run-001-uuid:run.start', 'run-001-uuid', 'run.start', '2026-02-09 10:00:00');

INSERT INTO knowledge_run_event (event_id, run_id, event_type, processed_at) VALUES
('run-001-uuid:run.final', 'run-001-uuid', 'run.final', '2026-02-09 10:00:15');

INSERT INTO knowledge_run_event (event_id, run_id, event_type, processed_at) VALUES
('run-001-uuid:tool.end:tc-001', 'run-001-uuid', 'tool.end', '2026-02-09 10:00:02');

INSERT INTO knowledge_run_event (event_id, run_id, event_type, processed_at) VALUES
('run-001-uuid:tool.end:tc-002', 'run-001-uuid', 'tool.end', '2026-02-09 10:00:03');

INSERT INTO knowledge_run_event (event_id, run_id, event_type, processed_at) VALUES
('run-002-uuid:run.start', 'run-002-uuid', 'run.start', '2026-02-09 10:05:00');

INSERT INTO knowledge_run_event (event_id, run_id, event_type, processed_at) VALUES
('run-002-uuid:run.final', 'run-002-uuid', 'run.final', '2026-02-09 10:05:21');

INSERT INTO knowledge_run_event (event_id, run_id, event_type, processed_at) VALUES
('run-003-uuid:run.start', 'run-003-uuid', 'run.start', '2026-02-09 10:10:00');

INSERT INTO knowledge_run_event (event_id, run_id, event_type, processed_at) VALUES
('run-004-uuid:run.failed', 'run-004-uuid', 'run.failed', '2026-02-09 10:15:55');
