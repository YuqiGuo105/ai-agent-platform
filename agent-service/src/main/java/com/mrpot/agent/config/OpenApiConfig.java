package com.mrpot.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI agentServiceOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("Agent Service API")
            .description("AI Agent Platform - Main service for RAG answer streaming with MCP tool integration")
            .version("1.0.0")
            .contact(new Contact()
                .name("Agent Platform Team")
                .email("yuqi.guo17@gmail.com")))
        .servers(List.of(
            new Server().url("http://localhost:8080").description("Local Development"),
            new Server().url("https://api.example.com").description("Production")
        ));
  }
}
