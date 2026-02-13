package com.mrpot.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileExtractionConfig {

  public static final int MAX_FILE_URLS = 3;

  @Value("${file.attach-timeout-seconds:30}")
  private long attachTimeoutSeconds;

  @Value("${file.max-concurrent:2}")
  private int maxConcurrent;

  @Value("${file.max-urls:3}")
  private int maxFileUrls;

  public long getAttachTimeoutSeconds() {
    return attachTimeoutSeconds;
  }

  public int getMaxConcurrent() {
    return maxConcurrent;
  }

  public int getMaxFileUrls() {
    return maxFileUrls;
  }
}
