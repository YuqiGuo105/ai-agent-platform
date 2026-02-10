# Yuqi's AI Agent Platform

A multi-module Spring Boot platform for building AI-powered agents with RAG (Retrieval-Augmented Generation), tool invocation, and telemetry capabilities.

## Architecture

```
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────────────────┐
│  agent-service  │────▶│  agent-tools-service │     │ agent-telemetry-service │
│    (Port 8080)  │     │     (Port 8081)      │     │      (Port 8082)        │
└────────┬────────┘     └──────────────────────┘     └────────────▲────────────┘
         │                                                        │
         │                    RabbitMQ                            │
         └────────────────────────────────────────────────────────┘
                              (Telemetry Events)
```

## Modules

| Module | Description | Port |
|--------|-------------|------|
| `agent-common` | Shared models, DTOs, and utilities | - |
| `agent-service` | Core AI agent service with RAG & streaming | 8080 |
| `agent-tools-service` | Tool registry and execution service | 8081 |
| `agent-telemetry-service` | Telemetry consumer and persistence | 8082 |

## Tech Stack

- **Java 21** + **Spring Boot 3.5.x**
- **Spring WebFlux** (agent-service) / **Spring WebMVC** (tools & telemetry)
- **PostgreSQL** (pgvector for embeddings)
- **RabbitMQ** (async telemetry messaging)
- **OpenAI API** (LLM & embeddings)
- **SpringDoc OpenAPI** (Swagger UI)

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker (for PostgreSQL & RabbitMQ)

### Run with Docker Compose

```bash
docker-compose up -d
```

### Build & Run Locally

```bash
# Build all modules
mvn clean install

# Run agent-service
cd agent-service && mvn spring-boot:run

# Run agent-tools-service (in another terminal)
cd agent-tools-service && mvn spring-boot:run

# Run agent-telemetry-service (in another terminal)
cd agent-telemetry-service && mvn spring-boot:run
```

## API Documentation (Swagger UI)

| Service | Swagger UI URL |
|---------|----------------|
| agent-service | http://localhost:8080/swagger-ui.html |
| agent-tools-service | http://localhost:8081/swagger-ui.html |
| agent-telemetry-service | http://localhost:8082/swagger-ui.html |

**OpenAPI JSON endpoints:**
- agent-service: http://localhost:8080/v3/api-docs
- agent-tools-service: http://localhost:8081/v3/api-docs
- agent-telemetry-service: http://localhost:8082/v3/api-docs

## Key Endpoints

### agent-service (8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/answer/stream` | SSE streaming answer with RAG |
| POST | `/kb/upload` | Upload documents to knowledge base |
| GET | `/actuator/health` | Health check |

### agent-tools-service (8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/tools` | List registered tools |
| POST | `/tools/{toolId}/invoke` | Invoke a tool |

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key | - |
| `OPENAI_BASE_URL` | OpenAI API base URL | `https://api.openai.com` |
| `RABBITMQ_URL` | RabbitMQ connection URL | `amqp://guest:guest@localhost:5672` |
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/aiagent` |

## Testing

```bash
# Run all tests
mvn test

# Run tests for specific module
cd agent-service && mvn test
```

## Telemetry Events

The platform emits the following telemetry events via RabbitMQ:

| Event Type | Description |
|------------|-------------|
| `run.start` | Agent run started |
| `run.rag_done` | RAG retrieval completed |
| `run.final` | Final answer generated |
| `run.failed` | Run failed with error |
| `run.cancelled` | Run cancelled |

## License

MIT
