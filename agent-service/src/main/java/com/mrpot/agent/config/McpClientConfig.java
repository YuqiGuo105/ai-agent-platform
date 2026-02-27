package com.mrpot.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class McpClientConfig {

  @Value("${mcp.tools.base-url}")
  private String toolsBaseUrl;

  @Bean
  public WebClient mcpWebClient() {
    reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
        .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
        .responseTimeout(java.time.Duration.ofMillis(30_000));

    return WebClient.builder()
        .baseUrl(toolsBaseUrl)
        .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
        .defaultHeader(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                       org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(org.springframework.http.HttpHeaders.ACCEPT,
                       org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
        .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .build();
  }
}
