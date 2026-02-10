package com.mrpot.agent.tools.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for OpenAI Vision API (ChatGPT with vision capabilities).
 */
@Component
public class OpenAiVisionClient {
  
  private static final Logger log = LoggerFactory.getLogger(OpenAiVisionClient.class);
  private static final ObjectMapper OM = new ObjectMapper();
  private static final int TIMEOUT_MS = 30000;
  
  private final WebClient webClient;
  private final String apiKey;
  
  public OpenAiVisionClient(WebClient.Builder webClientBuilder,
      @Value("${spring.ai.openai.api-key:}") String apiKey) {
    this.apiKey = apiKey;
    
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("OpenAI API key is not configured!");
    }
    
    this.webClient = webClientBuilder
        .baseUrl("https://api.openai.com/v1")
        .build();
    
    log.info("OpenAiVisionClient initialized");
  }
  
  /**
   * Call OpenAI Chat API with vision support.
   * 
   * @param messages list of message objects with role and content
   * @param extra additional parameters (temperature, top_p, max_tokens, etc.)
   * @return raw JSON response as string
   */
  public String chatCompletions(List<Map<String, Object>> messages, Map<String, Object> extra) {
    try {
      if (apiKey == null || apiKey.isBlank()) {
        log.error("OpenAI API key not configured");
        return "{\"error\":true,\"message\":\"API key not configured\"}";
      }
      
      // Build request body
      Map<String, Object> requestBody = new LinkedHashMap<>();
      requestBody.put("model", "gpt-4o-mini"); // Vision-capable model
      requestBody.put("messages", messages);
      
      // Add extra parameters (temperature, top_p, max_tokens, etc.)
      if (extra != null && !extra.isEmpty()) {
        requestBody.putAll(extra);
      }
      
      // Make API call
      String responseJson = webClient
          .post()
          .uri("/chat/completions")
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
          .bodyValue(requestBody)
          .retrieve()
          .bodyToMono(String.class)
          .timeout(Duration.ofMillis(TIMEOUT_MS))
          .block();
      
      return responseJson;
      
    } catch (Exception e) {
      log.error("Failed to call OpenAI Vision API: {}", e.getMessage(), e);
      // Return error as JSON
      Map<String, Object> errorResponse = new LinkedHashMap<>();
      errorResponse.put("error", true);
      errorResponse.put("message", e.getMessage());
      try {
        return OM.writeValueAsString(errorResponse);
      } catch (Exception jsonEx) {
        return "{\"error\":true,\"message\":\"" + e.getMessage() + "\"}";
      }
    }
  }
}
