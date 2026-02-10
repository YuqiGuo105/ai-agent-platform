package com.mrpot.agent.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentTelemetryServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentTelemetryServiceApplication.class, args);
  }
}
