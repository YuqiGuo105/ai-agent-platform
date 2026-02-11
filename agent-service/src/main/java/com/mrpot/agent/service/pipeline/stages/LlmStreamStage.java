package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.FileItem;
import com.mrpot.agent.service.LlmService;
import com.mrpot.agent.service.RagAnswerService;
import com.mrpot.agent.service.model.ChatMessage;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pipeline stage for LLM streaming.
 * Builds the prompt from context and streams the LLM response using Spring AI ChatClient.
 */
@Slf4j
@RequiredArgsConstructor
public class LlmStreamStage implements Processor<Void, Flux<SseEnvelope>> {
    
    private final RagAnswerService ragAnswerService;
    private final LlmService llmService;
    
    @Override
    public Mono<Flux<SseEnvelope>> process(Void input, PipelineContext context) {
        String prompt = buildPrompt(context);
        List<ChatMessage> history = getConversationHistory(context);
        
        log.info("LLM streaming: promptLength={}, historySize={} for runId={}", 
            prompt.length(), history.size(), context.runId());
        
        // Stream LLM response with actual DeepSeek integration
        Flux<SseEnvelope> responseFlux = streamLlmResponse(prompt, history, context);
        
        return Mono.just(responseFlux);
    }
    
    /**
     * Build the prompt from pipeline context.
     * Incorporates extracted files and RAG context.
     */
    private String buildPrompt(PipelineContext context) {
        StringBuilder prompt = new StringBuilder();
        
        // Add extracted file content
        List<FileItem> files = context.getExtractedFiles();
        if (!files.isEmpty()) {
            String filePrompt = ragAnswerService.fuseFilesIntoPrompt(files, "");
            if (!filePrompt.isBlank()) {
                prompt.append(filePrompt);
                prompt.append("\n\n");
            }
        }
        
        // Add RAG context
        String ragContext = context.getRagContext();
        if (ragContext != null && !ragContext.isBlank()) {
            prompt.append("【Knowledge base context】\n");
            prompt.append(ragContext);
            prompt.append("\n\n");
        }
        
        // Add user question
        prompt.append("【User question】\n");
        prompt.append(context.request().question());
        
        return prompt.toString();
    }
    
    /**
     * Get conversation history from pipeline context.
     */
    @SuppressWarnings("unchecked")
    private List<ChatMessage> getConversationHistory(PipelineContext context) {
        Object history = context.get(HistoryStage.KEY_CONVERSATION_HISTORY);
        if (history instanceof List) {
            return (List<ChatMessage>) history;
        }
        return Collections.emptyList();
    }
    
    /**
     * Stream the LLM response using Spring AI ChatClient.
     */
    private Flux<SseEnvelope> streamLlmResponse(String prompt, List<ChatMessage> history, PipelineContext context) {
        StringBuilder fullAnswer = new StringBuilder();
        
        return llmService.streamResponse(prompt, history)
            .map(chunk -> {
                fullAnswer.append(chunk);
                
                return new SseEnvelope(
                    StageNames.ANSWER_DELTA,
                    chunk,
                    Map.of("delta", chunk),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                );
            })
            .doOnComplete(() -> {
                // Store final answer in context
                context.setFinalAnswer(fullAnswer.toString());
                log.debug("LLM streaming complete: answerLength={} for runId={}",
                    fullAnswer.length(), context.runId());
            })
            .onErrorResume(e -> {
                log.error("LLM service error, falling back to simulated response: {}", e.getMessage());
                return streamFallbackResponse(context);
            });
    }
    
    /**
     * Fallback simulated response when LLM service fails.
     */
    private Flux<SseEnvelope> streamFallbackResponse(PipelineContext context) {
        String[] chunks = {"I apologize, ", "but I'm ", "currently ", "unable ", "to ", 
            "generate ", "a ", "response. ", "Please ", "try ", "again ", "later."};
        StringBuilder fullAnswer = new StringBuilder();
        
        return Flux.fromArray(chunks)
            .delayElements(Duration.ofMillis(30))
            .map(chunk -> {
                fullAnswer.append(chunk);
                
                return new SseEnvelope(
                    StageNames.ANSWER_DELTA,
                    chunk,
                    Map.of("delta", chunk),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                );
            })
            .doOnComplete(() -> {
                context.setFinalAnswer(fullAnswer.toString());
                log.debug("Fallback LLM streaming complete for runId={}", context.runId());
            });
    }
}
