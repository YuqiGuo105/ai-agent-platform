package com.mrpot.agent.tools.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.tool.FileUnderstanding;
import com.mrpot.agent.tools.client.QwenVlFlashClient;
import com.mrpot.agent.tools.client.OpenAiVisionClient;
import com.mrpot.agent.tools.config.AlibabaConfig;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service for handling file attachments from URLs.
 * Includes SSRF protection and content extraction.
 */
@Service
public class AttachmentService {
  
  private static final Logger logger = LoggerFactory.getLogger(AttachmentService.class);
  private static final ObjectMapper OM = new ObjectMapper();
  
  // SSRF protection: private IP ranges
  private static final String[] PRIVATE_RANGES = {
      "10.",              // 10.0.0.0/8
      "172.16.",          // 172.16.0.0/12
      "192.168.",         // 192.168.0.0/16
      "127.",             // 127.0.0.1
      "169.254."          // 169.254.0.0/16 (link-local)
  };
  
  private static final String MODEL = "qwen-vl-plus";
  private static final int TIMEOUT_MS = 30000;
  
  @Autowired
  private WebClient.Builder webClientBuilder;
  
  @Autowired
  private AlibabaConfig alibabaConfig;
  
  @Autowired
  private QwenVlFlashClient qwenVlClient;
  
  @Autowired
  private OpenAiVisionClient openAiVisionClient;
  
  /**
   * Understand file URL with Qwen VL.
   * 
   * @param fileUrl URL of the file
   * @return FileUnderstanding with extracted data
   */
  public Mono<FileUnderstanding> understandFileUrlWithQwenVl(String fileUrl) {
    return Mono.defer(() -> {
      // Validate URL for SSRF
      if (!isSafeUrl(fileUrl)) {
        logger.warn("URL rejected due to SSRF check: {}", fileUrl);
        return Mono.just(new FileUnderstanding("", List.of(), List.of(), "ssrf_check_failed"));
      }
      
      return getContentAndProcess(fileUrl);
    })
    .subscribeOn(Schedulers.boundedElastic());
  }
  
  /**
   * Understand file URL with OpenAI Vision.
   * 
   * @param fileUrl URL of the file
   * @return FileUnderstanding with extracted data
   */
  public Mono<FileUnderstanding> understandFileUrlWithOpenAi(String fileUrl) {
    return Mono.defer(() -> {
      // Validate URL for SSRF
      if (!isSafeUrl(fileUrl)) {
        logger.warn("URL rejected due to SSRF check: {}", fileUrl);
        return Mono.just(new FileUnderstanding("", List.of(), List.of(), "ssrf_check_failed"));
      }
      
      return getContentAndProcessOpenAi(fileUrl);
    })
    .subscribeOn(Schedulers.boundedElastic());
  }
  
  private Mono<FileUnderstanding> getContentAndProcess(String fileUrl) {
    // Get MIME type via HEAD request
    return detectMimeTypeMono(fileUrl)
        .flatMap(mimeType -> {
          if (mimeType == null || mimeType.isBlank()) {
            logger.warn("Could not detect MIME type for {}", fileUrl);
            return Mono.just(new FileUnderstanding("", List.of(), List.of(), "mime_type_detection_failed"));
          }
          
          // Download and process content (returns base64 encoded)
          return downloadAndProcessContentMono(fileUrl, mimeType)
              .flatMap(base64Content -> {
                if (base64Content == null || base64Content.isBlank()) {
                  logger.warn("Failed to download/encode content from {}", fileUrl);
                  return Mono.just(new FileUnderstanding("", List.of(), List.of(), "content_download_failed"));
                }
                
                logger.debug("Calling Qwen VL for file: {}", fileUrl);
                // Call Qwen VL with base64 content
                return understandContentMono(base64Content, mimeType);
                })
                .switchIfEmpty(Mono.just(new FileUnderstanding("", List.of(), List.of(), "content_download_failed")));
        })
        .onErrorResume(e -> {
          logger.error("Error processing file: {}", fileUrl, e);
          return Mono.just(new FileUnderstanding("", List.of(), List.of(), "processing_error: " + e.getMessage()));
        });
  }
  
  private Mono<FileUnderstanding> getContentAndProcessOpenAi(String fileUrl) {
    // Get MIME type via HEAD request
    return detectMimeTypeMono(fileUrl)
        .flatMap(mimeType -> {
          if (mimeType == null || mimeType.isBlank()) {
            logger.warn("Could not detect MIME type for {}", fileUrl);
            return Mono.just(new FileUnderstanding("", List.of(), List.of(), "mime_type_detection_failed"));
          }
          
          // Download and process content (returns base64 encoded)
          return downloadAndProcessContentMono(fileUrl, mimeType)
              .flatMap(base64Content -> {
                if (base64Content == null || base64Content.isBlank()) {
                  logger.warn("Failed to download/encode content from {}", fileUrl);
                  return Mono.just(new FileUnderstanding("", List.of(), List.of(), "content_download_failed"));
                }
                
                logger.debug("Calling OpenAI Vision for file: {}", fileUrl);
                // Call OpenAI Vision with base64 content
                return understandContentWithOpenAi(base64Content, mimeType);
                })
                .switchIfEmpty(Mono.just(new FileUnderstanding("", List.of(), List.of(), "content_download_failed")));
        })
        .onErrorResume(e -> {
          logger.error("Error processing file: {}", fileUrl, e);
          return Mono.just(new FileUnderstanding("", List.of(), List.of(), "processing_error: " + e.getMessage()));
        });
  }
  
  /**
   * Detect MIME type via HTTP HEAD request (reactive).
   */
  private Mono<String> detectMimeTypeMono(String fileUrl) {
    return webClientBuilder
        .build()
        .head()
        .uri(URI.create(fileUrl))
        .retrieve()
        .toEntity(Void.class)
        .map(response -> {
          var contentType = response.getHeaders().getContentType();
          String result = contentType != null ? contentType.toString() : guessFromUrl(fileUrl);
          return result;
        })
        .timeout(Duration.ofSeconds(5))
        .onErrorResume(e -> {
          String guessed = guessFromUrl(fileUrl);
          return Mono.just(guessed);
        });
  }
  
  /**
   * Download and process file content (reactive).
   * Returns base64 encoded content for Qwen VL processing.
   */
  private Mono<String> downloadAndProcessContentMono(String fileUrl, String mimeType) {
    return webClientBuilder
        .build()
        .get()
        .uri(URI.create(fileUrl))
        .retrieve()
        .bodyToMono(byte[].class)
        .timeout(Duration.ofSeconds(30))
        .map(content -> {
          // For images, base64 encode the bytes directly
          if (mimeType != null && mimeType.startsWith("image/")) {
            return Base64.getEncoder().encodeToString(content);
          }
          // For text, convert to string first, limit size, then base64
          String text = new String(content, StandardCharsets.UTF_8);
          Integer maxChars = alibabaConfig.getFile().getMaxTextChars();
          if (maxChars != null && maxChars > 0 && text.length() > maxChars) {
            text = text.substring(0, maxChars);
          }
          return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        })
        .onErrorResume(e -> {
          logger.error("Failed to download/process content from {}: {}", fileUrl, e.getMessage(), e);
          return Mono.empty();
        });
  }
  
  /**
   * Validate URL for SSRF protection.
   * Only allows https:// and rejects private IP ranges.
   */
  private boolean isSafeUrl(String urlStr) {
    if (urlStr == null || urlStr.isBlank()) {
      return false;
    }
    
    try {
      URL url = new URI(urlStr).toURL();
      
      // Only HTTPS
      if (!"https".equalsIgnoreCase(url.getProtocol())) {
        logger.warn("URL protocol not HTTPS: {}", urlStr);
        return false;
      }
      
      String host = url.getHost();
      
      // Check for localhost
      if ("localhost".equalsIgnoreCase(host)) {
        return false;
      }
      
      // Check for private IP ranges
      for (String range : PRIVATE_RANGES) {
        if (host.startsWith(range)) {
          logger.warn("URL host in private range: {}", host);
          return false;
        }
      }
      
      return true;
    } catch (Exception e) {
      logger.warn("Failed to parse URL: {}", urlStr, e);
      return false;
    }
  }
  
  /**
   * Guess MIME type from URL extension.
   */
  private String guessFromUrl(String fileUrl) {
    if (fileUrl.endsWith(".pdf")) return "application/pdf";
    if (fileUrl.endsWith(".txt")) return "text/plain";
    if (fileUrl.endsWith(".jpg") || fileUrl.endsWith(".jpeg")) return "image/jpeg";
    if (fileUrl.endsWith(".png")) return "image/png";
    if (fileUrl.endsWith(".gif")) return "image/gif";
    if (fileUrl.endsWith(".webp")) return "image/webp";
    return "application/octet-stream";
  }
  
  /**
   * Call Qwen VL to understand file content and extract text, keywords, queries.
   */
  private Mono<FileUnderstanding> understandContentMono(String base64Image, String mimeType) {
    return Mono.fromCallable(() -> {
      var config = alibabaConfig.getDashscope();
      if (config.getApiKey() == null || config.getApiKey().isBlank()) {
        logger.warn("DashScope API key not configured");
        return new FileUnderstanding(
            "",
            List.of(),
            List.of(),
            "api_key_not_configured"
        );
      }
      
      try {
        String prompt = buildPrompt();
        
        // Build content parts following the reference code pattern
        List<Map<String, Object>> contentParts = new ArrayList<>();
        
        // Add image URL part
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64Image);
        imagePart.put("image_url", imageUrl);
        contentParts.add(imagePart);
        
        // Add text prompt part
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        contentParts.add(textPart);
        
        // Call Qwen VL using the client
        String responseJson = callQwenWithParts(contentParts);
        return parseUnderstandingOrFallback(responseJson);
        
      } catch (Exception e) {
        logger.error("Failed to call Qwen VL API: {}", e.getMessage(), e);
        return new FileUnderstanding(
            "",
            List.of(),
            List.of(),
            "qwen_vl_error: " + e.getMessage()
        );
      }
    })
    .subscribeOn(Schedulers.boundedElastic());
  }
  
  /**
   * Call OpenAI Vision to understand file content and extract text, keywords, queries.
   */
  private Mono<FileUnderstanding> understandContentWithOpenAi(String base64Image, String mimeType) {
    return Mono.fromCallable(() -> {
      if (openAiVisionClient == null) {
        logger.warn("OpenAI Vision client not initialized");
        return new FileUnderstanding(
            "",
            List.of(),
            List.of(),
            "openai_client_not_initialized"
        );
      }
      
      try {
        String prompt = buildPrompt();
        
        // Build content parts following the same pattern as Qwen VL
        List<Map<String, Object>> contentParts = new ArrayList<>();
        
        // Add image URL part
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64Image);
        imagePart.put("image_url", imageUrl);
        contentParts.add(imagePart);
        
        // Add text prompt part
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        contentParts.add(textPart);
        
        // Call OpenAI Vision using the client
        String responseJson = callOpenAiWithParts(contentParts);
        return parseUnderstandingOrFallback(responseJson);
        
      } catch (Exception e) {
        logger.error("Failed to call OpenAI Vision API: {}", e.getMessage(), e);
        return new FileUnderstanding(
            "",
            List.of(),
            List.of(),
            "openai_vision_error: " + e.getMessage()
        );
      }
    })
    .subscribeOn(Schedulers.boundedElastic());
  }
  
  /**
   * Call Qwen VL with content parts (following reference code pattern).
   */
  private String callQwenWithParts(List<Map<String, Object>> contentParts) {
    Map<String, Object> userMsg = new LinkedHashMap<>();
    userMsg.put("role", "user");
    userMsg.put("content", contentParts);
    
    // Parameters aligned with reference code
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("temperature", 0.7);
    extra.put("top_p", 0.8);
    extra.put("max_tokens", 512);
    
    return qwenVlClient.chatCompletions(null, List.of(userMsg), extra);
  }
  
  /**
   * Call OpenAI Vision with content parts.
   */
  private String callOpenAiWithParts(List<Map<String, Object>> contentParts) {
    Map<String, Object> userMsg = new LinkedHashMap<>();
    userMsg.put("role", "user");
    userMsg.put("content", contentParts);
    
    // Parameters aligned with Qwen VL
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("temperature", 0.7);
    extra.put("top_p", 0.8);
    extra.put("max_tokens", 512);
    
    return openAiVisionClient.chatCompletions(List.of(userMsg), extra);
  }
  
  /**
   * Build extraction prompt for Qwen VL (simplified).
   */
  private String buildPrompt() {
    return """
Extract key info from this document/image.
Return JSON only: {"text":"","keywords":[],"queries":[]}
- text: max 300 chars summary
- keywords: max 10 key topics
- queries: max 5 search queries
Do not include markdown code blocks, just raw JSON.
""";
  }
  
  /**
   * Parse Qwen VL response and extract FileUnderstanding (following reference code pattern).
   */
  private FileUnderstanding parseUnderstandingOrFallback(String llmRaw) {
    String cleaned = cleanupModelOutput(llmRaw);
    
    // First try to parse the entire response as JSON from API
    FileUnderstanding r = tryParseApiResponse(cleaned);
    if (r == null) {
      // Try to extract JSON object from content
      String json = extractFirstJsonObject(cleaned);
      r = tryParseContentJson(json);
    }
    
    if (r == null) {
      // Fallback to text-only response
      return new FileUnderstanding(
          cleaned == null ? "" : cleaned.trim(),
          List.of(),
          List.of(),
          null
      );
    }
    
    return r;
  }
  
  private String cleanupModelOutput(String s) {
    if (s == null) return "";
    String t = s.trim();
    t = t.replaceAll("^```(?:json)?\\s*", "");
    t = t.replaceAll("\\s*```\\s*$", "");
    return t.trim();
  }
  
  /**
   * Try to parse full API response (with choices array).
   */
  private FileUnderstanding tryParseApiResponse(String responseJson) {
    try {
      JsonNode root = OM.readTree(responseJson);
      
      // Extract message content from choices[0].message.content
      JsonNode content = root.at("/choices/0/message/content");
      if (!content.isMissingNode()) {
        String contentStr = content.asText();
        return tryParseContentJson(contentStr);
      }
      
      return null;
    } catch (Exception e) {
      return null;
    }
  }
  
  /**
   * Try to parse content as FileUnderstanding JSON.
   */
  private FileUnderstanding tryParseContentJson(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    
    // Remove markdown code blocks if present
    if (t.startsWith("```")) {
      // Remove opening code block (```json, ```, etc.)
      t = t.replaceAll("^```(json)?\\s*", "");
      // Remove closing code block
      t = t.replaceAll("\\s*```\\s*$", "");
      t = t.trim();
    }
    
    if (!t.startsWith("{")) {
      return null;
    }
    
    try {
      JsonNode parsed = OM.readTree(t);
      
      String text = parsed.path("text").asText("");
      List<String> keywords = new ArrayList<>();
      parsed.path("keywords").forEach(k -> keywords.add(k.asText()));
      List<String> queries = new ArrayList<>();
      parsed.path("queries").forEach(q -> queries.add(q.asText()));
      
      return new FileUnderstanding(text, keywords, queries, null);
      
    } catch (Exception e) {
      return null;
    }
  }
  
  private String extractFirstJsonObject(String s) {
    if (s == null) return null;
    int start = s.indexOf('{');
    int end = s.lastIndexOf('}');
    if (start >= 0 && end > start) return s.substring(start, end + 1).trim();
    return null;
  }
}
