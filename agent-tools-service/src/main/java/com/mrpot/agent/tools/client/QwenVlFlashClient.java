package com.mrpot.agent.tools.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.tools.config.AlibabaConfig;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for Qwen VL Flash API (DashScope compatible endpoint).
 */
@Component
public class QwenVlFlashClient {
  
  private static final Logger log = LoggerFactory.getLogger(QwenVlFlashClient.class);
  private static final ObjectMapper OM = new ObjectMapper();
  private static final int TIMEOUT_MS = 30000;
  
  private final WebClient webClient;
  private final AlibabaConfig alibabaConfig;
  
  public QwenVlFlashClient(WebClient.Builder webClientBuilder, AlibabaConfig alibabaConfig) {
    this.alibabaConfig = alibabaConfig;
    
    // Build WebClient with proper base URL for DashScope
    var config = alibabaConfig.getDashscope();
    String baseUrl = config.getBaseUrl();
    String apiKey = config.getApiKey();
    
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
      log.warn("DashScope base URL not configured, using default: {}", baseUrl);
    }
    
    if (apiKey == null || apiKey.isBlank()) {
      log.error("DashScope API key is not configured!");
    }
    
    this.webClient = webClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .build();
    
    log.info("QwenVlFlashClient initialized with baseUrl: {}", baseUrl);
  }
  
  /**
   * Call Qwen VL chat completions API.
   * 
   * @param systemPrompt optional system prompt (can be null)
   * @param messages list of message objects with role and content
   * @param extra additional parameters (temperature, top_p, max_tokens, etc.)
   * @return raw JSON response as string
   */
  public String chatCompletions(String systemPrompt, List<Map<String, Object>> messages, Map<String, Object> extra) {
    try {
      // Build request body
      Map<String, Object> requestBody = new LinkedHashMap<>();
      requestBody.put("model", "qwen-vl-plus");
      
      // Add system message if provided
      List<Map<String, Object>> allMessages = new java.util.ArrayList<>();
      if (systemPrompt != null && !systemPrompt.isBlank()) {
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        allMessages.add(systemMsg);
      }
      allMessages.addAll(messages);
      
      requestBody.put("messages", allMessages);
      
      // Add extra parameters
      if (extra != null && !extra.isEmpty()) {
        requestBody.putAll(extra);
      }
      
      // Get the actual API key from config
      var config = alibabaConfig.getDashscope();
      String apiKey = config.getApiKey();
      
      // Make API call with explicit Authorization header
      String responseJson = webClient
          .post()
          .uri("/chat/completions")
          .header("Authorization", "Bearer " + apiKey)
          .bodyValue(requestBody)
          .retrieve()
          .bodyToMono(String.class)
          .timeout(Duration.ofMillis(TIMEOUT_MS))
          .block();
      
      return responseJson;
      
    } catch (Exception e) {
      log.error("Failed to call Qwen VL API: {}", e.getMessage(), e);
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
