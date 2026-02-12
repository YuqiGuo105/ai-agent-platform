package com.mrpot.agent.knowledge.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Agent Knowledge API",
        version = "1.0",
        description = "KB document management — list, get, delete, and fuzzy search"
    )
)
public class OpenApiConfig {
}
