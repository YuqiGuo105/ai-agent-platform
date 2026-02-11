package com.mrpot.agent.service;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.common.tool.FileItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.tool.FileUnderstanding;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service for RAG pipeline orchestration.
 * Handles file extraction, context fusion, and answer generation.
 */
@Service
public class RagAnswerService {
  
  private static final Logger logger = LoggerFactory.getLogger(RagAnswerService.class);
  private static final int MAX_FILE_URLS = 3;
  
  @Value("${file.attach-timeout-seconds:30}")
  private long attachTimeoutSeconds;
  
  @Value("${file.max-concurrent:2}")
  private int maxConcurrent;
  
  @Value("${file.max-urls:3}")
  private int maxFileUrls;
  
  private final ToolInvoker toolInvoker;
  private final ObjectMapper objectMapper = new ObjectMapper();
  
  public RagAnswerService(ToolInvoker toolInvoker) {
    this.toolInvoker = toolInvoker;
  }
  
  /**
   * Extract files from URLs with concurrent processing and timeout handling.
   * Returns a list of FileItem objects.
   */
  public Mono<List<FileItem>> extractFilesMono(List<String> urls) {
    if (urls == null || urls.isEmpty()) {
      return Mono.just(List.of());
    }
    
    // URL cleanup: trim, remove null/blank, deduplicate, limit to maxFileUrls
    List<String> safeUrls = urls.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .distinct()
        .limit(maxFileUrls)
        .toList();
    
    if (safeUrls.isEmpty()) {
      return Mono.just(List.of());
    }
    
    // Process URLs with concurrency limit and timeout
    return Flux.fromIterable(safeUrls)
        .flatMap(url ->
            Mono.fromCallable(() -> extractOneFileBlocking(url))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(attachTimeoutSeconds))
                .onErrorResume(ex -> {
                  logger.warn("Error extracting file from {}: {}", url, ex.getClass().getSimpleName());
                  return Mono.just(new FileItem(
                      url,
                      filenameFromUrl(url),
                      guessMimeFromUrl(url),
                      "",
                      List.of(),
                      List.of(),
                      "extract_failed: " + ex.getClass().getSimpleName()
                  ));
                }),
            maxConcurrent  // Max concurrent requests
        )
        .collectList();
  }
  
  /**
   * Extract a single file in blocking mode.
   */
  private FileItem extractOneFileBlocking(String url) {
    try {
      CallToolRequest toolRequest = new CallToolRequest(
          "file.understandUrl",
          objectMapper.createObjectNode().put("url", url),
          null,
          null,
          null,
          null
      );

      CallToolResponse response = toolInvoker.call(toolRequest).block();

      if (response == null || !response.ok()) {
        String errorMsg = response != null && response.error() != null
            ? response.error().message()
            : "unknown_error";
        return new FileItem(url, filenameFromUrl(url), guessMimeFromUrl(url),
            "", List.of(), List.of(), errorMsg);
      }

      FileUnderstanding understanding = parseFileUnderstanding(response.result());

      String keyText = understanding.text() == null ? "" : understanding.text().trim();
      if (keyText.isBlank()
          && understanding.keywords().isEmpty()
          && understanding.queries().isEmpty()) {
        return new FileItem(
            url,
            filenameFromUrl(url),
            guessMimeFromUrl(url),
            "",
            List.of(),
            List.of(),
            understanding.error() == null ? "extract_empty_result" : understanding.error()
        );
      }

      return new FileItem(
          url,
          filenameFromUrl(url),
          guessMimeFromUrl(url),
          keyText,
          understanding.keywords(),
          understanding.queries(),
          understanding.error()
      );
    } catch (Exception e) {
      logger.error("Exception extracting file from {}", url, e);
      return new FileItem(url, filenameFromUrl(url), guessMimeFromUrl(url),
          "", List.of(), List.of(), "exception: " + e.getClass().getSimpleName());
    }
  }

  /**
   * Parse FileUnderstanding from tool response.
   */
  private FileUnderstanding parseFileUnderstanding(JsonNode result) {
    try {
      String text = result.path("text").asText("");
      String errorMsg = result.path("error").asText(null);

      List<String> keywords = new ArrayList<>();
      result.path("keywords").forEach(k -> keywords.add(k.asText()));

      List<String> queries = new ArrayList<>();
      result.path("queries").forEach(q -> queries.add(q.asText()));

      return new FileUnderstanding(text, keywords, queries, errorMsg);
    } catch (Exception e) {
      logger.error("Failed to parse FileUnderstanding", e);
      return new FileUnderstanding("", List.of(), List.of(), "parse_error");
    }
  }
  
  /**
   * Generate SSE events for file extraction.
   */
  public Flux<SseEnvelope> generateFileExtractionEvents(
      List<String> urls,
      String traceId,
      String sessionId,
      AtomicLong seq
  ) {
    // Cleanup URLs
    List<String> safeUrls = urls.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .distinct()
        .limit(maxFileUrls)
        .toList();
    
    if (safeUrls.isEmpty()) {
      return Flux.empty();
    }
    
    // Start event with file list
    List<String> filenames = safeUrls.stream()
        .map(this::filenameFromUrl)
        .toList();
    Flux<SseEnvelope> startEvent = Flux.just(createEnvelope(
        StageNames.FILE_EXTRACT_START,
        "Extracting uploads",
        Map.of("fileCount", safeUrls.size(), "files", filenames),
        seq,
        traceId,
        sessionId
    ));
    
    // Extract files
    Flux<SseEnvelope> extractEvents = extractFilesMono(safeUrls)
        .flatMapMany(files -> Flux.fromIterable(files)
            .map(fileItem -> createEnvelope(
                StageNames.FILE_EXTRACT,
                "Extracted: " + fileItem.filename(),
                Map.of(
                    "filename", fileItem.filename(),
                    "contentPreview", fileItem.text().length() > 100 ?
                        fileItem.text().substring(0, 100) + "..." :
                        fileItem.text(),
                    "keywords", fileItem.keywords(),
                    "success", fileItem.isSuccess()
                ),
                seq,
                traceId,
                sessionId
            ))
        );
    
    // Done event
    Flux<SseEnvelope> doneEvent = extractFilesMono(safeUrls)
        .map(files -> {
          long successfulFiles = files.stream().filter(FileItem::isSuccess).count();
          int totalFiles = files.size();
          return createEnvelope(
              StageNames.FILE_EXTRACT_DONE,
              "File extraction complete",
              Map.of(
                  "totalFiles", totalFiles,
                  "successfulFiles", successfulFiles,
                  "summary", "Extracted content from " + successfulFiles + " files"
              ),
              seq,
              traceId,
              sessionId
          );
        })
        .flux();
    
    return startEvent.concatWith(extractEvents).concatWith(doneEvent);
  }
  
  /**
   * Fuse extracted files into LLM prompt.
   */
  public String fuseFilesIntoPrompt(List<FileItem> files, String originalQuestion) {
    StringBuilder prompt = new StringBuilder();
    
    int count = 1;
    for (FileItem file : files) {
      if (!file.isSuccess()) {
        continue;  // Skip failed extractions
      }
      
      prompt.append("【FILE#").append(count).append("】\n");
      prompt.append("- url: ").append(file.url()).append("\n");
      prompt.append("- mime: ").append(file.mime()).append("\n");
      prompt.append("- keywords: ").append(String.join(", ", file.keywords())).append("\n");
      
      boolean truncated = file.text().length() > 40000;
      prompt.append("- truncated: ").append(truncated).append("\n\n");
      
      // Add file content (limited length)
      int limit = 40000;
      String content = file.text();
      if (content.length() > limit) {
        content = content.substring(0, limit);
      }
      prompt.append(content).append("\n\n");
      
      count++;
    }
    
    if (count > 1 && !originalQuestion.isBlank()) {
      prompt.append("【Q】\n");
    }
    
    prompt.append(originalQuestion);
    
    return prompt.toString();
  }
  
  /**
   * Extract filename from URL.
   */
  private String filenameFromUrl(String url) {
    try {
      String path = new java.net.URI(url).getPath();
      if (path != null && !path.isEmpty()) {
        String[] parts = path.split("/");
        if (parts.length > 0) {
          return parts[parts.length - 1];
        }
      }
    } catch (Exception e) {
      logger.debug("Failed to extract filename from URL: {}", url);
    }
    return "file";
  }
  
  /**
   * Guess MIME type from URL extension.
   */
  private String guessMimeFromUrl(String url) {
    if (url.endsWith(".pdf")) return "application/pdf";
    if (url.endsWith(".txt")) return "text/plain";
    if (url.endsWith(".jpg") || url.endsWith(".jpeg")) return "image/jpeg";
    if (url.endsWith(".png")) return "image/png";
    if (url.endsWith(".gif")) return "image/gif";
    if (url.endsWith(".webp")) return "image/webp";
    return "application/octet-stream";
  }

  
  /**
   * Create SSE envelope.
   */
  private SseEnvelope createEnvelope(
      String stage,
      String message,
      Object payload,
      AtomicLong seq,
      String traceId,
      String sessionId
  ) {
    return new SseEnvelope(
        stage,
        message,
        payload,
        seq.incrementAndGet(),
        System.currentTimeMillis(),
        traceId,
        sessionId
    );
  }
}
