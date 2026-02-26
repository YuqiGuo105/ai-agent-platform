package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.service.RagAnswerService;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Pipeline stage for file extraction.
 * Extracts content from attached file URLs and stores them in context.
 * Emits SSE events for file extraction progress.
 */
@Slf4j
@RequiredArgsConstructor
public class FileExtractStage implements Processor<Void, Flux<SseEnvelope>> {
    
    private static final int MAX_FILES = 3;
    
    private final RagAnswerService ragAnswerService;
    
    @Override
    public Mono<Flux<SseEnvelope>> process(Void input, PipelineContext context) {
        // Get file URLs from request
        List<String> fileUrls = context.request().resolveFileUrls(MAX_FILES);
        
        if (fileUrls.isEmpty()) {
            log.debug("No files to extract for runId={}", context.runId());
            return Mono.just(Flux.empty());
        }
        
        log.info("Extracting {} files for runId={}", fileUrls.size(), context.runId());
        
        // Extract files and store them in context
        Mono<Void> extractionMono = ragAnswerService.extractFilesMono(fileUrls, context.runId())
            .doOnNext(files -> {
                context.setExtractedFiles(files);
                long successCount = files.stream().filter(f -> f.isSuccess()).count();
                log.info("File extraction complete: {} files, {} successful for runId={}", 
                    files.size(), successCount, context.runId());
            })
            .then();
        
        // Generate SSE events for file extraction progress
        Flux<SseEnvelope> eventFlux = ragAnswerService.generateFileExtractionEvents(
            fileUrls,
            context.traceId(),
            context.sessionId(),
            context.sseSeq()
        );
        
        // Return the event flux, which will include START, EXTRACT, and DONE events
        // The extractionMono runs as a side effect within generateFileExtractionEvents
        return Mono.just(eventFlux);
    }
}
