package com.mrpot.agent.telemetry.controller;

import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TraceQueryController using SQL test data.
 * 
 * Test data includes:
 * - 5 runs: run-001 to run-005 with various statuses (DONE, RUNNING, FAILED)
 * - 9 tool calls across the runs
 * - Sessions: session-abc-123 (2 runs), session-def-456 (2 runs), session-ghi-789 (1 run)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SqlGroup({
    @Sql(scripts = "classpath:test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
    @Sql(scripts = "classpath:cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
})
class TraceQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Test data constants matching SQL file
    private static final String RUN_ID_1 = "run-001-uuid";
    private static final String RUN_ID_2 = "run-002-uuid";
    private static final String RUN_ID_3 = "run-003-uuid";
    private static final String RUN_ID_4 = "run-004-uuid";
    private static final String RUN_ID_5 = "run-005-uuid";
    private static final String SESSION_ABC = "session-abc-123";
    private static final String SESSION_DEF = "session-def-456";
    private static final String SESSION_GHI = "session-ghi-789";

    @Nested
    @DisplayName("GET /api/runs/{runId}")
    class GetRunDetailTests {

        @Test
        @DisplayName("Should return run details with tool calls when run exists")
        void getRunDetail_existingRun_returnsRunWithTools() throws Exception {
            mockMvc.perform(get("/api/runs/{runId}", RUN_ID_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID_1))
                .andExpect(jsonPath("$.traceId").value("trace-xyz-001"))
                .andExpect(jsonPath("$.sessionId").value(SESSION_ABC))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.mode").value("GENERAL"))
                .andExpect(jsonPath("$.model").value("deepseek"))
                .andExpect(jsonPath("$.totalLatencyMs").value(1500))
                .andExpect(jsonPath("$.kbHitCount").value(3))
                .andExpect(jsonPath("$.tools", hasSize(2)))
                .andExpect(jsonPath("$.tools[0].toolCallId").value("tc-001"))
                .andExpect(jsonPath("$.tools[0].toolName").value("kb_search"))
                .andExpect(jsonPath("$.tools[0].ok").value(true))
                .andExpect(jsonPath("$.tools[1].toolCallId").value("tc-002"))
                .andExpect(jsonPath("$.tools[1].toolName").value("llm_generate"));
        }

        @Test
        @DisplayName("Should return run with multiple tool calls")
        void getRunDetail_runWithMultipleTools_returnsAllTools() throws Exception {
            mockMvc.perform(get("/api/runs/{runId}", RUN_ID_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID_2))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.kbHitCount").value(5))
                .andExpect(jsonPath("$.tools", hasSize(3)))
                .andExpect(jsonPath("$.tools[*].toolName", containsInAnyOrder("kb_search", "web_search", "llm_generate")));
        }

        @Test
        @DisplayName("Should return 404 when run does not exist")
        void getRunDetail_nonExistingRun_returns404() throws Exception {
            mockMvc.perform(get("/api/runs/{runId}", "non-existent-run-id"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return running status run correctly")
        void getRunDetail_runningRun_returnsCorrectStatus() throws Exception {
            mockMvc.perform(get("/api/runs/{runId}", RUN_ID_3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID_3))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.kbHitCount").value(0))
                .andExpect(jsonPath("$.tools", hasSize(0)));  // No tools for this run
        }

        @Test
        @DisplayName("Should return failed run with error info")
        void getRunDetail_failedRun_returnsWithToolFailures() throws Exception {
            mockMvc.perform(get("/api/runs/{runId}", RUN_ID_4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID_4))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.tools", hasSize(2)))  // 2 retry attempts
                .andExpect(jsonPath("$.tools[0].ok").value(false))
                .andExpect(jsonPath("$.tools[0].errorCode").value("TIMEOUT"))
                .andExpect(jsonPath("$.tools[1].attempt").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/runs/{runId}/tools")
    class GetToolCallsTests {

        @Test
        @DisplayName("Should return tool calls for existing run")
        void getToolCalls_existingRun_returnsToolList() throws Exception {
            mockMvc.perform(get("/api/runs/{runId}/tools", RUN_ID_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].toolCallId").value("tc-001"))
                .andExpect(jsonPath("$[0].toolName").value("kb_search"))
                .andExpect(jsonPath("$[0].ok").value(true))
                .andExpect(jsonPath("$[0].durationMs").value(250))
                .andExpect(jsonPath("$[0].cacheHit").value(false))
                .andExpect(jsonPath("$[1].toolCallId").value("tc-002"))
                .andExpect(jsonPath("$[1].toolName").value("llm_generate"));
        }

        @Test
        @DisplayName("Should return empty list when no tools for run")
        void getToolCalls_noTools_returnsEmptyList() throws Exception {
            mockMvc.perform(get("/api/runs/{runId}/tools", RUN_ID_3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should return failed tool calls with error info")
        void getToolCalls_failedTools_returnsErrorInfo() throws Exception {
            mockMvc.perform(get("/api/runs/{runId}/tools", RUN_ID_4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].ok").value(false))
                .andExpect(jsonPath("$[0].errorCode").value("TIMEOUT"))
                .andExpect(jsonPath("$[0].errorMsg", containsString("timeout")))
                .andExpect(jsonPath("$[0].retryable").value(true));
        }

        @Test
        @DisplayName("Should return cache hit information")
        void getToolCalls_withCacheHit_showsCacheInfo() throws Exception {
            mockMvc.perform(get("/api/runs/{runId}/tools", RUN_ID_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cacheHit").value(true))  // tc-003 has cacheHit=true
                .andExpect(jsonPath("$[0].freshness").value("FRESH"));
        }
    }

    @Nested
    @DisplayName("GET /api/runs")
    class SearchRunsTests {

        @Test
        @DisplayName("Should return all runs when no filters provided")
        void searchRuns_noFilters_returnsAllRuns() throws Exception {
            mockMvc.perform(get("/api/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));  // 5 runs in test data
        }

        @Test
        @DisplayName("Should filter runs by sessionId")
        void searchRuns_sessionIdFilter_returnsFilteredRuns() throws Exception {
            mockMvc.perform(get("/api/runs")
                    .param("sessionId", SESSION_ABC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))  // run-001 and run-002
                .andExpect(jsonPath("$[*].sessionId", everyItem(equalTo(SESSION_ABC))));
        }

        @Test
        @DisplayName("Should filter runs by status DONE")
        void searchRuns_statusDone_returnsOnlyDone() throws Exception {
            mockMvc.perform(get("/api/runs")
                    .param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))  // run-001, run-002, run-005
                .andExpect(jsonPath("$[*].status", everyItem(equalTo("DONE"))));
        }

        @Test
        @DisplayName("Should filter runs by status RUNNING")
        void searchRuns_statusRunning_returnsOnlyRunning() throws Exception {
            mockMvc.perform(get("/api/runs")
                    .param("status", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))  // run-003 only
                .andExpect(jsonPath("$[0].id").value(RUN_ID_3));
        }

        @Test
        @DisplayName("Should filter runs by status FAILED")
        void searchRuns_statusFailed_returnsOnlyFailed() throws Exception {
            mockMvc.perform(get("/api/runs")
                    .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))  // run-004 only
                .andExpect(jsonPath("$[0].id").value(RUN_ID_4))
                .andExpect(jsonPath("$[0].errorCode").value("TIMEOUT"));
        }

        @Test
        @DisplayName("Should filter runs by both sessionId and status")
        void searchRuns_bothFilters_returnsFilteredRuns() throws Exception {
            mockMvc.perform(get("/api/runs")
                    .param("sessionId", SESSION_DEF)
                    .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))  // run-004 only
                .andExpect(jsonPath("$[0].id").value(RUN_ID_4));
        }

        @Test
        @DisplayName("Should return empty list when no runs match filters")
        void searchRuns_noMatches_returnsEmptyList() throws Exception {
            mockMvc.perform(get("/api/runs")
                    .param("sessionId", "non-existent-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should ignore blank filter values")
        void searchRuns_blankFilters_treatedAsNoFilter() throws Exception {
            mockMvc.perform(get("/api/runs")
                    .param("sessionId", "  ")
                    .param("status", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));  // Returns all runs
        }

        @Test
        @DisplayName("Should filter runs for session with single run")
        void searchRuns_sessionWithOneRun_returnsSingleRun() throws Exception {
            mockMvc.perform(get("/api/runs")
                    .param("sessionId", SESSION_GHI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(RUN_ID_5))
                .andExpect(jsonPath("$[0].userId").value("user-003"));
        }
    }
}
