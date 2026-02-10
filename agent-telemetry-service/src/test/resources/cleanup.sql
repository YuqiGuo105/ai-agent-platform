-- =============================================
-- Cleanup Script for Telemetry Service Tests
-- =============================================
-- Truncates all telemetry tables in correct order
-- respecting foreign key dependencies
-- =============================================

TRUNCATE TABLE knowledge_run_event;
TRUNCATE TABLE es_outbox;
TRUNCATE TABLE telemetry_dlq_message;
TRUNCATE TABLE knowledge_tool_call;
TRUNCATE TABLE knowledge_run;
