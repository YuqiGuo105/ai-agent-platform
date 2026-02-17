# AI Agent Platform

> Multi-module AI Agent Platform (SSE + Tools + Telemetry)

A production-ready, multi-module Spring Boot platform for building AI-powered agents with **RAG** (Retrieval-Augmented Generation), **tool invocation**, **knowledge base management**, and **telemetry** capabilities. The platform supports multiple LLM providers (DeepSeek, OpenAI, Google Gemini, DashScope/Qwen) and communicates via **Server-Sent Events (SSE)** for real-time streaming responses.

---

## Table of Contents

- [Architecture](#architecture)
- [Key Components and Connections](#key-components-and-connections)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Project Setup](#project-setup)
- [Environment Variables](#environment-variables)
- [API Documentation (Swagger UI)](#api-documentation-swagger-ui)
- [Monitoring and Observability](#monitoring-and-observability)
- [DEEP Mode Configuration](#deep-mode-configuration)
- [Telemetry Events](#telemetry-events)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## Architecture

```
                          +----------------------------+
                          |    agent-knowledge-ui      |
                          |     (Frontend / React)     |
                          +-------------+--------------+
                                        | HTTP
                                        v
+----------------------+  HTTP   +----------------------+  HTTP   +----------------------------+
|   agent-service      |<------->| agent-tools-service  |         |   agent-knowledge          |
|     (Port 8080)      |         |    (Port 8081)       |         |     (Port 8083)            |
+----------+-----------+         +----------------------+         +----------------------------+
           |                                                                  |
           |         RabbitMQ (Telemetry Events)                              |
           +------------------------+------------------------------------------+
                                    v
                     +-------------------------------+
                     |  agent-telemetry-service       |
                     |       (Port 8082)              |
                     +-------------------------------+
                                    |
           +------------------------+------------------------+
           v                        v                        v
   +---------------+     +----------------+       +----------------+
   |  PostgreSQL   |     | Elasticsearch  |       |  Prometheus    |
   |  (pgvector)   |     |                |       |  + Grafana     |
   +---------------+     +----------------+       +----------------+
```

---

## Key Components and Connections

### Modules

| Module | Description | Port |
|---|---|---|
| `agent-common` | Shared models, DTOs, and utilities used across all services | - |
| `agent-knowledge` | Knowledge base management service with S3 storage and vector search | 8083 |
| `agent-knowledge-ui` | Frontend interface for knowledge base management | - |
| `agent-service` | Main AI agent service with RAG, SSE streaming, and DEEP mode reasoning | 8080 |
| `agent-tools-service` | Tool registry and execution service for agent tool invocation | 8081 |
| `agent-telemetry-service` | Telemetry collection, persistence, and Elasticsearch sync | 8082 |

### Service Connections

- **agent-service** connects to **agent-tools-service** via `TOOLS_SERVICE_URL` (default: `http://localhost:8081`) for tool discovery and invocation.
- **agent-tools-service** connects back to **agent-service** via `AGENT_SERVICE_URL` (default: `http://agent-service:8080`) for knowledge base retrieval.
- Services communicate via **RabbitMQ** for asynchronous telemetry event publishing and consumption.
- All services use **PostgreSQL** with the **pgvector** extension for data storage and vector similarity search.

### Infrastructure Components

| Component | Purpose | Port(s) |
|---|---|---|
| **PostgreSQL** (pgvector) | Primary data store with vector search support | 5432 |
| **Redis** | Caching layer (LRU eviction, 256MB max) | 6379 |
| **RabbitMQ** | Message queue for telemetry events | 5672 (AMQP), 15672 (Management UI) |
| **Elasticsearch** | Search and analytics (optional) | 9200, 9300 |
| **Kibana** | Elasticsearch visualization UI | 5601 |
| **S3-compatible storage** (Tigris) | File/document storage for knowledge base | - |
| **Prometheus** | Metrics collection | 9090 |
| **Grafana** | Metrics visualization and dashboards | 3000 |

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.x**
- **Spring AI 1.1.2** (multi-provider LLM support)
- **Spring WebFlux** (agent-service) / **Spring WebMVC** (tools & telemetry)
- **PostgreSQL** with **pgvector** extension
- **Redis** (caching)
- **RabbitMQ** (async messaging)
- **Elasticsearch** (search & analytics)
- **Prometheus + Grafana** (monitoring)
- **Lombok 1.18.36**
- **SpringDoc OpenAPI 2.3.0** (Swagger UI)
- **OkHttp3 4.12.0**
- **Docker & Docker Compose**

---

## Prerequisites

| Requirement | Version |
|---|---|
| **Java** | 21 (specified in `pom.xml`) |
| **Maven** | 3.9+ (or use included Maven wrapper `./mvnw`) |
| **Docker & Docker Compose** | Latest stable (for running infrastructure) |
| **LLM API Key** | At least one of: DeepSeek, OpenAI, Google Gemini, or DashScope |

---

## Project Setup

### 1. Clone the Repository

```bash
git clone https://github.com/YuqiGuo105/ai-agent-platform.git
cd ai-agent-platform
```

### 2. Configure Environment Variables

```bash
cp .env.example .env
# Edit .env and fill in your API keys and configuration
```

### 3. Build the Project

```bash
# Build all modules
mvn clean install

# Or skip tests for faster build
mvn clean install -DskipTests

# Using the Maven wrapper (no Maven installation required)
./mvnw clean install -DskipTests
```

### 4. Run with Docker Compose

```bash
# Start infrastructure only (PostgreSQL, Redis, RabbitMQ, Elasticsearch, monitoring)
docker-compose up -d

# Start infrastructure + all application services
docker-compose --profile app up -d

# Stop all services and remove volumes
docker-compose down -v
```

### 5. Run Locally (without Docker for app services)

Start infrastructure via Docker Compose, then run each service individually:

```bash
# Start infrastructure
docker-compose up -d

# Run agent-service (terminal 1)
cd agent-service && mvn spring-boot:run

# Run agent-tools-service (terminal 2)
cd agent-tools-service && mvn spring-boot:run

# Run agent-telemetry-service (terminal 3)
cd agent-telemetry-service && mvn spring-boot:run

# Run agent-knowledge (terminal 4)
cd agent-knowledge && mvn spring-boot:run
```

### 6. Build Individual Docker Images

```bash
# Build agent-service image
docker build -f agent-service/Dockerfile -t agent-service .

# Build agent-tools-service image
docker build -f agent-tools-service/Dockerfile -t agent-tools-service .

# Build agent-telemetry-service image
docker build -f agent-telemetry-service/Dockerfile -t agent-telemetry-service .

# Build agent-knowledge image
docker build -f agent-knowledge/Dockerfile -t agent-knowledge .
```

> **Note:** All Docker builds must be run from the project root directory (context is `.`), as the multi-stage Dockerfiles reference the parent `pom.xml` and `agent-common` module.

---

## Environment Variables

Copy `.env.example` to `.env` and configure the following variables:

### Database

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/aiagent` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | - |

### Message Queue

| Variable | Description | Default |
|---|---|---|
| `RABBITMQ_URL` | RabbitMQ AMQP connection URL | `amqp://username:password@host:5672` |
| `RABBITMQ_USER` | RabbitMQ username | - |
| `RABBITMQ_PASS` | RabbitMQ password | - |

### Cache

| Variable | Description | Default |
|---|---|---|
| `REDIS_URL` | Redis connection URL | `redis://localhost:6379` |

### LLM Providers

| Variable | Description | Default |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API key | - |
| `OPENAI_CHAT_MODEL` | OpenAI chat model | `gpt-4.1-mini` |
| `OPENAI_TEMPERATURE` | OpenAI temperature | `0.3` |
| `OPENAI_EMBEDDING_MODEL` | OpenAI embedding model | `text-embedding-3-small` |
| `OPENAI_EMBEDDING_DIMENSIONS` | Embedding dimensions | `1536` |
| `DEEPSEEK_API_KEY` | DeepSeek API key | - |
| `DEEPSEEK_BASE_URL` | DeepSeek API base URL | `https://api.deepseek.com` |
| `DEEPSEEK_CHAT_MODEL` | DeepSeek chat model | `deepseek-chat` |
| `DEEPSEEK_TEMPERATURE` | DeepSeek temperature | `0.2` |
| `GEMINI_API_KEY` | Google Gemini API key (optional) | - |
| `GEMINI_CHAT_MODEL` | Gemini chat model | `gemini-2.0-flash` |
| `GEMINI_TEMPERATURE` | Gemini temperature | `0.3` |
| `DASHSCOPE_API_KEY` | DashScope (Qwen) API key | - |
| `DASHSCOPE_BASE_URL` | DashScope API base URL | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` |
| `DASHSCOPE_CHAT_MODEL` | DashScope chat model | `qwen-vl-plus` |
| `QWEN_TEMPERATURE` | Qwen temperature | `0.1` |
| `QWEN_MAX_TOKENS` | Qwen max tokens | `2048` |
| `QWEN_ENABLE_THINKING` | Enable Qwen thinking mode | `false` |

### Vision Model

| Variable | Description | Default |
|---|---|---|
| `VISION_PROVIDER` | Vision provider for file content extraction (`qwen-vl` or `openai`) | `qwen-vl` |

### Vector Store

| Variable | Description | Default |
|---|---|---|
| `PGVECTOR_DIMENSIONS` | PGVector embedding dimensions | `1536` |

### S3 / Tigris Storage

| Variable | Description | Default |
|---|---|---|
| `S3_ENDPOINT` | S3-compatible endpoint URL | `https://t3.storageapi.dev` |
| `S3_REGION` | S3 region | `auto` |
| `S3_BUCKET` | S3 bucket name | - |
| `S3_ACCESS_KEY` | S3 access key | - |
| `S3_SECRET_KEY` | S3 secret key | - |
| `S3_PRESIGN_TTL_SECONDS` | Pre-signed URL TTL in seconds | `900` |

### Elasticsearch

| Variable | Description | Default |
|---|---|---|
| `ELASTIC_BASE_URL` | Elasticsearch base URL | - |
| `ELASTIC_USERNAME` | Elasticsearch username | - |
| `ELASTIC_PASSWORD` | Elasticsearch password | - |
| `ELASTIC_STORE_TO_ES` | Enable storing data to Elasticsearch | `true` |
| `ELASTIC_ES_SYNC` | Enable Elasticsearch sync | `true` |
| `ELASTIC_ES_INDEX` | Elasticsearch index name | `mrpot_candidates` |

### Service Configuration

| Variable | Description | Default |
|---|---|---|
| `PORT` | Server port | `8080` |
| `CORS_ALLOWED_ORIGINS` | CORS allowed origins (comma-separated) | `*` |
| `TOOLS_SERVICE_URL` | URL of agent-tools-service | `http://localhost:8081` |
| `AGENT_SERVICE_URL` | URL of agent-service (used by tools-service) | `http://agent-service:8080` |

### Docker Compose Defaults

When using `docker-compose`, infrastructure connection variables are automatically configured in the compose file. You only need to provide API keys in your `.env` file:

```env
DEEPSEEK_API_KEY=your_deepseek_api_key
OPENAI_API_KEY=your_openai_api_key
GEMINI_API_KEY=your_gemini_api_key
DASHSCOPE_API_KEY=your_dashscope_api_key
```

---

## API Documentation (Swagger UI)

Each service exposes interactive API documentation via Swagger UI:

| Service | Swagger UI | OpenAPI Docs |
|---|---|---|
| agent-service | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| agent-tools-service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| agent-telemetry-service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| agent-knowledge | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |

---

## Monitoring and Observability

### Dashboards and UIs

| Tool | URL | Credentials |
|---|---|---|
| **Prometheus** | http://localhost:9090 | - |
| **Grafana** | http://localhost:3000 | `admin` / `admin` |
| **RabbitMQ Management** | http://localhost:15672 | `aiagent` / `aiagent_pwd` |
| **Kibana** | http://localhost:5601 | - |

### Health Check Endpoints

All services expose Spring Boot Actuator health endpoints:

| Service | Health Endpoint |
|---|---|
| agent-service | http://localhost:8080/actuator/health |
| agent-tools-service | http://localhost:8081/actuator/health |
| agent-telemetry-service | http://localhost:8082/actuator/health |
| agent-knowledge | http://localhost:8083/actuator/health |

Additional actuator endpoints exposed: `health`, `info`, `prometheus`

### Prometheus Metrics

All services expose Prometheus-compatible metrics at `/actuator/prometheus`. Prometheus is pre-configured to scrape these endpoints (see `prometheus/prometheus.yml`).

### Grafana Dashboards

Grafana is provisioned with dashboards automatically via `grafana/provisioning/`. Access Grafana at http://localhost:3000 with default credentials `admin`/`admin`.

---

## DEEP Mode Configuration

DEEP (Dynamic Evaluation and Execution Pipeline) mode enables multi-round reasoning with configurable parameters:

| Parameter | Description | Default |
|---|---|---|
| `deep.max-rounds-cap` | Maximum reasoning rounds | `5` |
| `deep.reasoning-timeout-seconds` | Total timeout for reasoning phase | `120` |
| `deep.confidence-threshold` | Confidence threshold to stop early | `0.85` |
| `deep.plan-timeout-seconds` | Timeout for plan generation | `30` |
| `deep.complexity-threshold` | Complexity threshold for automatic DEEP mode activation (0.0-1.0) | `0.6` |
| `deep.max-tool-rounds-cap` | Maximum tool call rounds | `10` |
| `deep.tool-timeout-seconds` | Timeout for individual tool calls | `30` |

These values are configured in `agent-service/src/main/resources/application.yaml`.

---

## Telemetry Events

The platform emits the following telemetry events via RabbitMQ, consumed by `agent-telemetry-service`:

| Event Type | Description |
|---|---|
| `run.start` | Agent run started |
| `run.rag_done` | RAG retrieval completed |
| `run.final` | Final answer generated |
| `run.failed` | Run failed with error |
| `run.cancelled` | Run cancelled |

The telemetry service persists events to PostgreSQL and syncs them to Elasticsearch via an outbox pattern with configurable batch size, retry limits, and cleanup intervals.

---

## Testing

```bash
# Run all tests across all modules
mvn test

# Run tests for a specific module
mvn test -pl agent-service

# Run tests for agent-tools-service
mvn test -pl agent-tools-service

# Run tests with verbose output
mvn test -pl agent-service -Dsurefire.useFile=false
```

---

## Troubleshooting

### Common Issues

**Port conflicts:**
If a port is already in use, change the port mapping in `docker-compose.yml` or set the `PORT` environment variable for the service.

**Database connection refused:**
Ensure PostgreSQL is running and healthy:
```bash
docker-compose ps postgres
docker-compose logs postgres
```

**pgvector extension not found:**
The Docker Compose setup uses `pgvector/pgvector:pg16` which includes the extension pre-installed. If running PostgreSQL manually, install pgvector:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

**RabbitMQ connection issues:**
Check RabbitMQ health and management UI at http://localhost:15672:
```bash
docker-compose logs rabbitmq
```

**Missing API keys:**
Ensure at least one LLM provider API key is configured in your `.env` file. The services will fail to start without valid API keys.

**Docker build fails:**
Always build from the project root directory since Dockerfiles reference the parent POM and `agent-common` module:
```bash
# Correct - run from project root
docker build -f agent-service/Dockerfile -t agent-service .

# Incorrect - do not run from the module directory
cd agent-service && docker build .  # This will fail
```

**Elasticsearch high memory usage:**
Elasticsearch is configured with 1GB heap (`-Xms1g -Xmx1g`). Reduce this in `docker-compose.yml` if running on a memory-constrained machine, or disable Elasticsearch if not needed.

---

## License

MIT
