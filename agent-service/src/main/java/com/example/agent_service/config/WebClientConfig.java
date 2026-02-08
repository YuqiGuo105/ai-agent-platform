package com.example.agent_service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class WebClientConfig {

  @Bean
  WebClient toolsWebClient(WebClient.Builder builder, McpProperties properties) {
    return builder.baseUrl(properties.toolsBaseUrl()).build();
  }
}
