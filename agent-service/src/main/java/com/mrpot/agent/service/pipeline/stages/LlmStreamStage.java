package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.common.kb.KbHit;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.FileItem;
import com.mrpot.agent.service.LlmService;
import com.mrpot.agent.service.RagAnswerService;
import com.mrpot.agent.model.ChatMessage;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    
    /** Max number of KB documents to include in the prompt. */
    private static final int MAX_KB_DOCS = 3;

    /** Minimum relevance score to include a KB doc in the prompt. */
    private static final double MIN_RELEVANCE_SCORE = 0.3;

    /** Pattern to detect answer blocks (e.g. "回答：..." or "回答:..."). */
    private static final Pattern ANSWER_PATTERN = Pattern.compile("回答[：:](.*)", Pattern.DOTALL);

    /**
     * Build the prompt from pipeline context.
     * Incorporates extracted files, RAG context (top 3 relevant docs), and user question.
     * Filters out low-relevance KB docs (score < 0.3) to avoid language contamination.
     * Extracts answer blocks from KB docs into【QA】section.
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

        // Process RAG documents: filter by score, limit to top 3, separate QA from KB
        List<KbDocument> ragDocs = context.getRagDocs();
        List<KbHit> ragHits = context.getRagHits();
        List<String> kbEntries = new ArrayList<>();
        List<String> qaEntries = new ArrayList<>();

        int docLimit = Math.min(ragDocs.size(), MAX_KB_DOCS);
        for (int i = 0; i < docLimit; i++) {
            // Skip docs below relevance threshold
            if (i < ragHits.size() && ragHits.get(i).score() < MIN_RELEVANCE_SCORE) {
                log.debug("Skipping KB doc {} with low score {}", i, ragHits.get(i).score());
                continue;
            }

            KbDocument doc = ragDocs.get(i);
            String content = doc.content();
            if (content == null || content.isBlank()) continue;

            Matcher matcher = ANSWER_PATTERN.matcher(content);
            if (matcher.find()) {
                qaEntries.add(matcher.group(1).trim());
            } else {
                kbEntries.add(content.trim());
            }
        }

        // Append【QA】section (curated answers from KB)
        if (!qaEntries.isEmpty()) {
            prompt.append("【QA】\n");
            for (int i = 0; i < qaEntries.size(); i++) {
                prompt.append("A").append(i + 1).append(": ").append(qaEntries.get(i)).append("\n");
            }
            prompt.append("\n");
        }

        // Append【KB】section (reference material)
        if (!kbEntries.isEmpty()) {
            prompt.append("【KB】\n");
            prompt.append(String.join("\n\n", kbEntries));
            prompt.append("\n\n");
        }

        // Highlight user question
        prompt.append("=== USER QUESTION (respond in this language) ===\n");
        prompt.append("【Q】\n");
        prompt.append(context.request().question());
        prompt.append("\n===");

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
    
    /** Pattern to strip echoed markers (e.g. 【QA】, 【KB】) from LLM output. */
    private static final Pattern MARKER_PATTERN = Pattern.compile("[\\s]*【(QA|KB|Q|FILE|HIS)】[\\s]*");

    /**
     * Stream the LLM response using Spring AI ChatClient.
     * Strips any echoed section markers from the output.
     */
    private Flux<SseEnvelope> streamLlmResponse(String prompt, List<ChatMessage> history, PipelineContext context) {
        StringBuilder fullAnswer = new StringBuilder();
        
        return llmService.streamResponse(prompt, history)
            .map(chunk -> {
                // Strip markers from every chunk
                String cleaned = MARKER_PATTERN.matcher(chunk).replaceAll("");
                fullAnswer.append(cleaned);
                
                return new SseEnvelope(
                    StageNames.ANSWER_DELTA,
                    cleaned,
                    Map.of("delta", cleaned),
                    context.nextSeq(),
                    System.currentTimeMillis(),
                    context.traceId(),
                    context.sessionId()
                );
            })
            .filter(envelope -> !envelope.message().isEmpty())
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
