package com.mrpot.agent;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test that loads the full Spring Boot application context.
 * 
 * Note: This test requires a PostgreSQL database with pgvector extension.
 * To run this test:
 * 1. Start PostgreSQL with pgvector extension
 * 2. Configure database connection in application-test.yaml
 * 3. Remove @Disabled annotation
 */
@Disabled("Requires PostgreSQL with pgvector extension running")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AgentServiceApplicationTests {

  @Test
  void contextLoads() {
  }
}

