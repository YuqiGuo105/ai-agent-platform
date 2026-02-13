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
import com.mrpot.agent.config.FileExtractionConfig;
import com.mrpot.agent.exception.FileExtractionException;
import com.mrpot.agent.exception.ToolInvocationException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  
  private final FileExtractionConfig fileExtractionConfig;
  private final ToolInvoker toolInvoker;
  private final ObjectMapper objectMapper = new ObjectMapper();
  
  public RagAnswerService(FileExtractionConfig fileExtractionConfig, ToolInvoker toolInvoker) {
    this.fileExtractionConfig = fileExtractionConfig;
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
        .limit(fileExtractionConfig.getMaxFileUrls())
        .toList();
    
    if (safeUrls.isEmpty()) {
      return Mono.just(List.of());
    }
    
    return Flux.fromIterable(safeUrls)
        .flatMap(url ->
            Mono.fromCallable(() -> extractOneFileBlocking(url))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(fileExtractionConfig.getAttachTimeoutSeconds()))
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
            fileExtractionConfig.getMaxConcurrent()
        )
        .collectList();
  }
  
  private FileItem extractOneFileBlocking(String url) {
    try {
      CallToolResponse response = invokeFileTool(url);
      FileItem errorItem = validateToolResponse(response, url);
      if (errorItem != null) {
        return errorItem;
      }
      FileUnderstanding understanding = parseFileUnderstanding(response.result());
      return buildFileItem(url, understanding);
    } catch (ToolInvocationException e) {
      logger.error("Tool invocation failed for {}: {}", url, e.getMessage(), e);
      throw new FileExtractionException(url, "tool_invocation_failed: " + e.getMessage(), e);
    } catch (FileExtractionException e) {
      logger.error("File extraction failed for {}: {}", url, e.getMessage(), e);
      return new FileItem(url, filenameFromUrl(url), guessMimeFromUrl(url),
          "", List.of(), List.of(), "exception: " + e.getClass().getSimpleName());
    } catch (Exception e) {
      logger.error("Unexpected exception extracting file from {}", url, e);
      throw new FileExtractionException(url, "exception: " + e.getClass().getSimpleName(), e);
    }
  }

  private CallToolResponse invokeFileTool(String url) {
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
      if (response == null) {
        throw new ToolInvocationException("file.understandUrl", "null response for url: " + url);
      }
      return response;
    } catch (ToolInvocationException e) {
      throw e;
    } catch (Exception e) {
      throw new ToolInvocationException("file.understandUrl", "invocation failed for url: " + url, e);
    }
  }

  private FileItem validateToolResponse(CallToolResponse response, String url) {
    if (!response.ok()) {
      String errorMsg = response.error() != null
          ? response.error().message()
          : "unknown_error";
      return new FileItem(url, filenameFromUrl(url), guessMimeFromUrl(url),
          "", List.of(), List.of(), errorMsg);
    }
    return null;
  }

  private FileItem buildFileItem(String url, FileUnderstanding understanding) {
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
  
  public Flux<SseEnvelope> generateFileExtractionEvents(
      List<String> urls,
      String traceId,
      String sessionId,
      AtomicLong seq
  ) {
    List<String> safeUrls = urls.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .distinct()
        .limit(fileExtractionConfig.getMaxFileUrls())
        .toList();
    
    if (safeUrls.isEmpty()) {
      return Flux.empty();
    }
    
    List<String> filenames = safeUrls.stream()
        .map(this::filenameFromUrl)
        .toList();
    Flux<SseEnvelope> startEvent = Flux.just(
        SseEnvelope.builder()
            .stage(StageNames.FILE_EXTRACT_START)
            .message("Extracting uploads")
            .payload(Map.of("fileCount", safeUrls.size(), "files", filenames))
            .seq(seq.incrementAndGet())
            .ts(System.currentTimeMillis())
            .traceId(traceId)
            .sessionId(sessionId)
            .build()
    );
    
    Flux<SseEnvelope> extractEvents = extractFilesMono(safeUrls)
        .flatMapMany(files -> Flux.fromIterable(files)
            .map(fileItem -> SseEnvelope.builder()
                .stage(StageNames.FILE_EXTRACT)
                .message("Extracted: " + fileItem.filename())
                .payload(Map.of(
                    "filename", fileItem.filename(),
                    "contentPreview", fileItem.text().length() > 100 ?
                        fileItem.text().substring(0, 100) + "..." :
                        fileItem.text(),
                    "keywords", fileItem.keywords(),
                    "success", fileItem.isSuccess()
                ))
                .seq(seq.incrementAndGet())
                .ts(System.currentTimeMillis())
                .traceId(traceId)
                .sessionId(sessionId)
                .build()
            )
        );
    
    Flux<SseEnvelope> doneEvent = extractFilesMono(safeUrls)
        .map(files -> {
          long successfulFiles = files.stream().filter(FileItem::isSuccess).count();
          int totalFiles = files.size();
          return SseEnvelope.builder()
              .stage(StageNames.FILE_EXTRACT_DONE)
              .message("File extraction complete")
              .payload(Map.of(
                  "totalFiles", totalFiles,
                  "successfulFiles", successfulFiles,
                  "summary", "Extracted content from " + successfulFiles + " files"
              ))
              .seq(seq.incrementAndGet())
              .ts(System.currentTimeMillis())
              .traceId(traceId)
              .sessionId(sessionId)
              .build();
        })
        .flux();
    
    return startEvent.concatWith(extractEvents).concatWith(doneEvent);
  }
  
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
  
  private String guessMimeFromUrl(String url) {
    if (url.endsWith(".pdf")) return "application/pdf";
    if (url.endsWith(".txt")) return "text/plain";
    if (url.endsWith(".jpg") || url.endsWith(".jpeg")) return "image/jpeg";
    if (url.endsWith(".png")) return "image/png";
    if (url.endsWith(".gif")) return "image/gif";
    if (url.endsWith(".webp")) return "image/webp";
    return "application/octet-stream";
  }

}
