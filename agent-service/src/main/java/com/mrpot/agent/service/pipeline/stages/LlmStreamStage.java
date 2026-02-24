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
import java.util.concurrent.atomic.AtomicReference;
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

        // Highlight user question - response language must match this question ONLY
        prompt.append("=== USER QUESTION ===\n");
        prompt.append("IMPORTANT: Respond in the SAME LANGUAGE as this question below, regardless of context language.\n");
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
    
    /**
     * Pattern to strip echoed markers from the BEGINNING of LLM output only (start-anchored, no MULTILINE).
     * Handles various marker formats:
     * - Chinese brackets: 【QA】, 【KB】, etc.
     * - ASCII brackets: [QA], [KB], [Conclusion], [Evidence Sources], [Uncertainty Declaration], etc.
     * - Citation markers: :codex-terminal-citation[...]{...}【QA】etc.
     * - Optional leading whitespace/newlines and trailing whitespace after markers
     */
    private static final Pattern LEADING_MARKER_PATTERN = Pattern.compile(
        "^[\\s\\r\\n]*" +
        "(?:" +
            // Citation prefix like :codex-terminal-citation[...]{...}
            ":?[a-z-]*citation[^\\]]*\\][^}]*\\}\\s*" +
        ")?" +
        "(?:" +
            // Standard markers with Chinese or ASCII brackets
            "[【\\[](?:QA|KB|Q|FILE|HIS|回答|Conclusion|Evidence Sources|Uncertainty Declaration)[】\\]]" +
            "[\\s]*(?:\\r?\\n)*" +
        ")*"
    );

    /**
     * Pattern to detect partial/incomplete markers at the end of a chunk.
     * Used to buffer text that might be part of a split marker.
     */
    private static final Pattern PARTIAL_MARKER_PATTERN = Pattern.compile(
        "(?:" +
            ":?[a-z-]*citation[^\\]]*$" +  // Incomplete citation
            "|\\][^}]*$" +                   // Citation bracket closed but braces incomplete
            "|[【\\[][^】\\]]*$" +             // Incomplete bracket marker
        ")"
    );

    /**
     * Pattern to match markers anywhere in text (for cleaning response content).
     * Matches markers like [QA], [Conclusion], [Evidence Sources], [Uncertainty Declaration], etc.
     */
    private static final Pattern INLINE_MARKER_PATTERN = Pattern.compile(
        "[【\\[](?:QA|KB|Q|FILE|HIS|回答|Conclusion|Evidence Sources|Uncertainty Declaration)[】\\]]\\s*(?:\\r?\\n)?",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Strip leading markers from the beginning of text.
     * This is a reusable helper for both per-chunk streaming and final-answer persistence.
     *
     * @param text the text to process
     * @return text with leading markers removed
     */
    private static String stripLeadingMarkers(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // Use start-anchored pattern (no MULTILINE) to only strip from the beginning
        String result = LEADING_MARKER_PATTERN.matcher(text).replaceFirst("");
        // Also strip any remaining leading whitespace until real content
        return result.replaceFirst("^[\\s\\n\\r]*", "");
    }

    /**
     * Stream the LLM response using Spring AI ChatClient.
     * Strips any echoed section markers from the output, including markers split across chunks.
     */
    private Flux<SseEnvelope> streamLlmResponse(String prompt, List<ChatMessage> history, PipelineContext context) {
        StringBuilder fullAnswer = new StringBuilder();
        AtomicBoolean leadingStripped = new AtomicBoolean(false);
        // Buffer to accumulate text that might contain split markers at the beginning
        AtomicReference<StringBuilder> leadingBuffer = new AtomicReference<>(new StringBuilder());
        
        // Pass execution mode to LlmService - FAST mode uses humorous tone
        return llmService.streamResponse(prompt, history, context.executionMode())
            .map(chunk -> {
                String cleaned = chunk;
                
                // Handle leading markers that may be split across chunks
                if (!leadingStripped.get()) {
                    StringBuilder buffer = leadingBuffer.get();
                    buffer.append(chunk);
                    String buffered = buffer.toString();
                    
                    // Check if buffer might contain a partial marker at the end
                    Matcher partialMatcher = PARTIAL_MARKER_PATTERN.matcher(buffered);
                    if (partialMatcher.find() && buffered.length() < 200) {
                        // Partial marker detected, wait for more chunks (but not indefinitely)
                        return "";
                    }
                    
                    // No partial marker or buffer is large enough - process the buffer
                    cleaned = stripLeadingMarkers(buffered);
                    
                    if (!cleaned.isEmpty()) {
                        leadingStripped.set(true);
                        leadingBuffer.set(new StringBuilder()); // Clear buffer
                    } else {
                        // Buffer was entirely markers, reset it
                        leadingBuffer.set(new StringBuilder());
                        return "";
                    }
                }
                
                // Remove any inline markers like [Conclusion], [QA], etc. from all chunks
                cleaned = INLINE_MARKER_PATTERN.matcher(cleaned).replaceAll("");
                fullAnswer.append(cleaned);
                
                return cleaned;
            })
            .filter(text -> text != null && !text.isEmpty())
            .map(cleaned -> new SseEnvelope(
                StageNames.ANSWER_DELTA,
                cleaned,
                Map.of("delta", cleaned),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            ))
            .doOnComplete(() -> {
                // Flush any remaining buffer content
                StringBuilder buffer = leadingBuffer.get();
                if (buffer.length() > 0 && !leadingStripped.get()) {
                    String remaining = stripLeadingMarkers(buffer.toString());
                    remaining = INLINE_MARKER_PATTERN.matcher(remaining).replaceAll("");
                    fullAnswer.append(remaining);
                }
                
                // Store final answer in context using the same stripLeadingMarkers helper
                String finalAnswer = stripLeadingMarkers(fullAnswer.toString());
                finalAnswer = INLINE_MARKER_PATTERN.matcher(finalAnswer).replaceAll("");
                context.setFinalAnswer(finalAnswer);
                log.debug("LLM streaming complete: answerLength={} for runId={}",
                    finalAnswer.length(), context.runId());
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
