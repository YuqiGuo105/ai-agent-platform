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
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** Pattern to normalize answer markers from RAG results. */
    private static final Pattern ANSWER_MARKER_PATTERN = Pattern.compile("\\[回答\\]|【回答】");

    /**
     * Build the prompt from pipeline context.
     * Incorporates extracted files, RAG context (top 3 relevant docs), and user question.
     * Filters out low-relevance KB docs (score < 0.3) to avoid language contamination.
     * Preserves full RAG content (not limited to answer blocks), while normalizing
     * any "[回答]"/"【回答】" markers to "[QA]"/"【QA】".
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

        // Process RAG documents: filter by score, limit to top 3
        List<KbDocument> ragDocs = context.getRagDocs();
        List<KbHit> ragHits = context.getRagHits();
        List<String> kbEntries = new ArrayList<>();

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

            String normalizedContent = ANSWER_MARKER_PATTERN.matcher(content)
                .replaceAll(match -> "【回答】".equals(match.group()) ? "【QA】" : "[QA]");
            kbEntries.add(normalizedContent.trim());
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
    
    /** Pattern to strip echoed markers from the beginning of LLM output, including trailing newlines. */
    private static final Pattern MARKER_PATTERN = Pattern.compile("^[\\s]*[【\\[](?:QA|KB|Q|FILE|HIS)[】\\]][\\s]*(?:\\r?\\n)*");

    /**
     * Stream the LLM response using Spring AI ChatClient.
     * Strips any echoed section markers from the output.
     */
    private Flux<SseEnvelope> streamLlmResponse(String prompt, List<ChatMessage> history, PipelineContext context) {
        StringBuilder fullAnswer = new StringBuilder();
        AtomicBoolean leadingStripped = new AtomicBoolean(false);
        StringBuilder leadingBuffer = new StringBuilder();
        
        // Pass execution mode to LlmService - FAST mode uses humorous tone
        return llmService.streamResponse(prompt, history, context.executionMode())
            .map(chunk -> {
                String cleaned = chunk;
                // Buffer initial chunks so split markers like "【" + "QA" + "】" are stripped reliably.
                if (!leadingStripped.get()) {
                    leadingBuffer.append(chunk);
                    cleaned = stripLeadingMarkers(leadingBuffer.toString());
                    if (!cleaned.isEmpty()) {
                        leadingStripped.set(true);
                        leadingBuffer.setLength(0);
                    }
                }
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
                // Store final answer in context, stripping any remaining markers
                String finalAnswer = stripLeadingMarkers(fullAnswer.toString());
                context.setFinalAnswer(finalAnswer);
                log.debug("LLM streaming complete: answerLength={} for runId={}",
                    finalAnswer.length(), context.runId());
            })
            .onErrorResume(e -> {
                log.error("LLM service error, falling back to simulated response: {}", e.getMessage());
                return streamFallbackResponse(context);
            });
    }

    private String stripLeadingMarkers(String text) {
        String stripped = text;
        String previous;
        do {
            previous = stripped;
            stripped = MARKER_PATTERN.matcher(stripped).replaceFirst("");
        } while (!stripped.equals(previous));
        return stripped.replaceFirst("^[\\s\\n\\r]+", "");
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
