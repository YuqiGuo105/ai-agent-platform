package com.mrpot.agent.tools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Alibaba Cloud configuration properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "alibaba")
public class AlibabaConfig {
  
  private DashScopeConfig dashscope = new DashScopeConfig();
  private FileConfig file = new FileConfig();
  
  @Data
  public static class DashScopeConfig {
    private String apiKey;
    private String baseUrl;
  }
  
  @Data
  public static class FileConfig {
    private Integer maxPdfPages;
    private Integer pdfDpi;
    private Integer maxTextChars;
    private String visionProvider;
  }
}
