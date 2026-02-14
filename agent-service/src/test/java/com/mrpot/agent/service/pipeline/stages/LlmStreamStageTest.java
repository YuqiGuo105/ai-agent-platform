package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.policy.ExecutionPolicy;
import com.mrpot.agent.common.policy.ToolAccessLevel;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.service.LlmService;
import com.mrpot.agent.service.RagAnswerService;
import com.mrpot.agent.service.pipeline.PipelineContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmStreamStageTest {

    @Test
    void process_stripsSplitLeadingMarkerFromFastModeStream() {
        RagAnswerService ragAnswerService = mock(RagAnswerService.class);
        LlmService llmService = mock(LlmService.class);
        when(llmService.streamResponse(any(), anyList(), eq("FAST")))
            .thenReturn(Flux.just("【", "QA", "】", "  \n", "hello", " world"));

        LlmStreamStage stage = new LlmStreamStage(ragAnswerService, llmService);
        PipelineContext context = new PipelineContext(
            "run-1",
            "trace-1",
            "sess-1",
            "user-1",
            new RagAnswerRequest("test", "sess-1", null, List.of(), ScopeMode.AUTO, null, "FAST", null, Map.of()),
            ScopeMode.AUTO,
            new ExecutionPolicy(true, true, ToolAccessLevel.NONE, 1, false, "FAST"),
            "FAST"
        );

        List<SseEnvelope> chunks = stage.process(null, context)
            .block()
            .collectList()
            .block();

        assertEquals(List.of("hello", " world"), chunks.stream().map(SseEnvelope::message).toList());
        assertEquals("hello world", context.getFinalAnswer());
    }
}
