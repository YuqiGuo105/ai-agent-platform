package com.mrpot.agent.tools.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

  @Value("${TOOLS_SERVICE_URL:http://localhost:8081}")
  private String toolsServiceUrl;

  @Value("${TOOLS_SERVICE_PROD_URL:https://tools-api.example.com}")
  private String toolsServiceProdUrl;

  @Bean
  public OpenAPI toolsServiceOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("Agent Tools Service API")
            .description("MCP (Model Context Protocol) Tools Service - Tool registration and execution")
            .version("1.0.0")
            .contact(new Contact()
                .name("Agent Platform Team")
                .email("yuqi.guo17@gmail.com")))
        .servers(List.of(
            new Server().url(toolsServiceUrl).description("Local Development"),
            new Server().url(toolsServiceProdUrl).description("Production")
        ));
  }
}

