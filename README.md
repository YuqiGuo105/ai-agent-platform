# AI Agent Platform

A microservices-based AI question-answering platform with two processing modes: **RAG (Retrieval-Augmented Generation)** for standard queries and **DEEP Mode** for complex reasoning tasks. Built with Spring Boot, Spring WebFlux, and React.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Core Systems](#core-systems)
  - [Pipeline Processing System](#1-pipeline-processing-system)
  - [Knowledge Base System](#2-knowledge-base-system)
  - [Tool Execution System](#3-tool-execution-system)
  - [Telemetry System](#4-telemetry-system)
  - [Frontend](#5-frontend)
- [Pipeline Stages](#pipeline-stages)
  - [RAG Pipeline (FAST Mode)](#rag-pipeline-fast-mode)
  - [DEEP Mode Pipeline](#deep-mode-pipeline)
- [Key Classes & Interfaces](#key-classes--interfaces)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Getting Started](#getting-started)
- [Docker Deployment](#docker-deployment)
- [Monitoring](#monitoring)
- [Technology Stack](#technology-stack)
- [Glossary](#glossary)

---

## Overview

### Purpose

The platform enables users to ask questions and receive AI-powered answers augmented by a knowledge base. It supports two processing modes selected by query complexity scoring:

| Mode | Trigger | Pipeline Stages | Description |
|---|---|---|---|
| **FAST (RAG)** | Complexity <= 0.6 | 4 stages | Retrieve relevant docs, stream LLM answer, save conversation |
| **DEEP** | Complexity > 0.6 | 6 stages | Plan, multi-round reasoning, tool orchestration, verification, reflection, synthesis |

### Target Users

| User | Capabilities |
|---|---|
| **End Users** | Ask questions via SSE streaming, upload files for context, receive RAG or DEEP answers |
| **Knowledge Managers** | Upload documents, manage knowledge base via UI, preview chunks before saving |
| **System Operators** | Monitor health via Grafana/Prometheus, view execution traces in telemetry dashboard, manage DLQ |

---

## Architecture

```
                           ┌──────────────────────┐
                           │  agent-knowledge-ui  │
                           │   React + Firebase   │
                           │     (Port 3000)      │
                           └──────────┬───────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
              v                       v                       v
┌─────────────────────┐ ┌──────────────────────┐ ┌─────────────────────┐
│   agent-service     │ │  agent-knowledge     │ │ agent-telemetry     │
│  Pipeline + SSE     │ │  KB CRUD + Vector    │ │ Event Processing    │
│   (Port 8080)       │ │   (Port 8083)        │ │  (Port 8082)        │
└──────────┬──────────┘ └──────────────────────┘ └──────────▲──────────┘
           │                                                │
           │            ┌──────────────────────┐            │
           ├───────────>│ agent-tools-service   │            │
           │  MCP calls │  Tool Execution       │            │
           │            │   (Port 8081)         │            │
           │            └──────────────────────┘            │
           │                                                │
           │                  RabbitMQ                       │
           └────────────────────────────────────────────────┘
                          (Telemetry Events)

     ┌──────────┐    ┌──────────┐    ┌───────────────┐    ┌──────────┐
     │PostgreSQL│    │  Redis   │    │ Elasticsearch │    │ S3/Tigris│
     │+ PGvector│    │  Cache   │    │  (Telemetry)  │    │ (Files)  │
     └──────────┘    └──────────┘    └───────────────┘    └──────────┘
```

### Services

| Service | Port | Framework | Role |
|---|---|---|---|
| **agent-service** | 8080 | Spring WebFlux | Core pipeline orchestrator. Handles RAG and DEEP processing, SSE streaming |
| **agent-tools-service** | 8081 | Spring WebMVC | Tool execution engine. Integrates with multiple AI providers via MCP protocol |
| **agent-telemetry-service** | 8082 | Spring WebMVC | Asynchronous event processing. Transactional outbox pattern to Elasticsearch |
| **agent-knowledge** | 8083 | Spring WebMVC | Knowledge base CRUD, vector search, document upload with presigned URLs |
| **agent-knowledge-ui** | 3000 | React + Vite | Web interface. Firebase auth, KB browser, upload UI, telemetry dashboard |
| **agent-common** | -- | Library | Shared models, DTOs, SSE stage constants |

### Infrastructure

| Component | Purpose |
|---|---|
| **PostgreSQL 16** (pgvector) | Primary database. Stores KB documents with vector embeddings (1536-dim HNSW index) |
| **Redis 7** | Conversation history cache, session storage |
| **RabbitMQ 3** | Async telemetry event delivery (with DLQ) |
| **Elasticsearch 8** | Telemetry event indexing and search |
| **Prometheus** | Metrics scraping (15s interval) |
| **Grafana** | Dashboard visualization |
| **Kibana** | Elasticsearch log exploration |

---

## Project Structure

```
ai-agent-platform/
├── agent-common/                          # Shared library (no port)
│   └── src/main/java/com/mrpot/agent/common/
│       ├── api/
│       │   ├── RagAnswerRequest.java      # Request DTO (question, fileUrls, sessionId, mode)
│       │   └── ScopeMode.java             # FAST / DEEP enum
│       ├── deep/
│       │   ├── VerificationReport.java    # Consistency score, contradictions, claims
│       │   └── ReflectionNote.java        # Follow-up action (retry/proceed)
│       ├── kb/
│       │   ├── KbDocument.java            # Knowledge base document model
│       │   └── KbHit.java                 # Search result with score
│       ├── policy/
│       │   └── ExecutionPolicy.java       # Pipeline execution parameters
│       ├── sse/
│       │   └── StageNames.java            # SSE event type constants (ANSWER_DELTA, DEEP_PLAN_DONE, etc.)
│       └── tool/
│           └── FileItem.java              # Extracted file content model
│
├── agent-service/                         # Core pipeline (Port 8080)
│   └── src/main/java/com/mrpot/agent/
│       ├── controller/
│       │   ├── AnswerStreamController.java  # POST /answer/stream -- SSE endpoint
│       │   └── KbController.java            # KB search proxy
│       ├── service/
│       │   ├── pipeline/
│       │   │   ├── PipelineContext.java      # Central state container (720 lines)
│       │   │   ├── Processor.java            # Stage interface: Mono<O> process(I, ctx)
│       │   │   ├── PipelineFactory.java      # Creates FAST or DEEP pipeline by complexity
│       │   │   ├── PipelineRunner.java       # Executes pipeline stages sequentially
│       │   │   ├── FastPipeline.java          # RAG pipeline assembly
│       │   │   ├── DeepPipeline.java          # DEEP pipeline assembly with retry loop
│       │   │   ├── DeepArtifactStore.java     # Typed accessors for DEEP artifacts
│       │   │   ├── DeepReasoningCoordinator.java # Multi-round reasoning orchestration
│       │   │   ├── DeepModeConfig.java        # @ConfigurationProperties for DEEP params
│       │   │   └── stages/
│       │   │       ├── HistoryStage.java          # Retrieve conversation from Redis
│       │   │       ├── FileExtractStage.java      # Extract content from uploaded files
│       │   │       ├── RagRetrieveStage.java      # Semantic search (score >= 0.3, top 3)
│       │   │       ├── LlmStreamStage.java        # Prompt construction + LLM streaming
│       │   │       ├── ConversationSaveStage.java  # Save to Redis
│       │   │       ├── TelemetryStage.java         # Emit telemetry events
│       │   │       ├── DeepPlanStage.java          # Generate structured plan (30s timeout)
│       │   │       ├── DeepReasoningStage.java     # Multi-round reasoning (120s, max 5 rounds)
│       │   │       ├── DeepToolOrchestrationStage.java # Execute tools from plan
│       │   │       ├── DeepVerificationStage.java  # Consistency + fact checking
│       │   │       ├── DeepReflectionStage.java    # Decide retry vs proceed
│       │   │       └── DeepSynthesisStage.java     # Build final answer with evidence
│       │   ├── LlmService.java              # LLM abstraction (DeepSeek primary)
│       │   └── RagAnswerService.java        # File extraction orchestration
│       └── config/                          # Spring configuration
│
├── agent-tools-service/                   # Tool execution (Port 8081)
│   └── src/main/java/com/mrpot/agent/tools/
│       ├── controller/
│       │   └── McpToolsController.java      # MCP protocol endpoints
│       ├── service/
│       │   ├── ToolRegistry.java            # Tool discovery and registration
│       │   ├── ToolHandler.java             # Base tool handler interface
│       │   └── ToolContext.java             # Execution context
│       ├── tool/
│       │   ├── FileTools.java               # file.understandUrl (vision AI extraction)
│       │   ├── KbSearchTool.java            # kb.search (vector search)
│       │   ├── KbGetDocumentTool.java       # kb.getDocument
│       │   └── deep/
│       │       ├── ReasoningAnalyzeTools.java   # reasoning.analyze
│       │       ├── ReasoningCompareTools.java   # reasoning.compare
│       │       ├── MemoryStoreTools.java        # memory.store
│       │       ├── MemoryRecallTools.java       # memory.recall
│       │       ├── VerifyConsistencyTool.java   # verify.consistency
│       │       ├── VerifyFactCheckTool.java     # verify.fact_check
│       │       ├── PlanningDecomposeTools.java  # planning.decompose
│       │       └── PlanningNextStepTools.java   # planning.next_step
│       └── client/
│           ├── OpenAiVisionClient.java      # GPT-4o-mini vision
│           └── QwenVlFlashClient.java       # Alibaba Qwen VL
│
├── agent-knowledge/                       # KB management (Port 8083)
│   └── src/main/java/com/mrpot/agent/knowledge/
│       ├── controller/
│       │   └── KbController.java            # 10 RESTful endpoints
│       ├── service/
│       │   ├── KbManagementService.java     # Pagination, search business logic
│       │   └── KbUploadService.java         # Chunking, embedding generation
│       └── repository/
│           └── KbDocumentRepository.java    # JdbcTemplate + PGvector
│
├── agent-telemetry-service/               # Event processing (Port 8082)
│   └── src/main/java/com/mrpot/agent/telemetry/
│       ├── consumer/
│       │   ├── RunLogConsumer.java          # RabbitMQ @RabbitListener
│       │   └── TelemetryDlqListener.java   # Dead letter queue handler
│       ├── controller/
│       │   ├── TraceQueryController.java    # Query telemetry data
│       │   ├── ReplayController.java        # Replay past runs
│       │   └── DlqController.java           # DLQ management
│       ├── worker/
│       │   └── EsOutboxWorker.java          # Polls outbox -> bulk index to ES
│       ├── service/
│       │   ├── ElasticsearchService.java    # ES bulk operations
│       │   └── TelemetryProjector.java      # Event projection
│       └── entity/                          # JPA entities
│
├── agent-knowledge-ui/                    # React frontend (Port 3000)
│   ├── src/
│   │   ├── contexts/AuthContext.jsx        # Firebase auth + demo mode fallback
│   │   ├── components/ProtectedRoute.jsx   # Auth route guard
│   │   ├── pages/
│   │   │   ├── LoginPage.jsx               # Firebase email/password + Google OAuth
│   │   │   ├── HomePage.jsx                # KB document browser with search
│   │   │   ├── UploadPage.jsx              # File/text upload with chunk preview
│   │   │   └── telemetry/
│   │   │       └── RunLogsDashboard.jsx    # Execution traces viewer
│   │   ├── services/telemetryApi.js        # Telemetry API client
│   │   ├── axios.js                        # Configured API client
│   │   └── firebase.js                     # Firebase initialization
│   ├── Dockerfile                          # Multi-stage: Node build -> Nginx
│   └── vite.config.js                      # Dev server with API proxy
│
├── docker-compose.yml                     # Full stack orchestration (12 services)
├── docker-compose.prod.yml                # Production overrides
├── prometheus/prometheus.yml              # Metrics scraping config
├── grafana/provisioning/                  # Auto-provisioned dashboards
├── initdb/01-init-extensions.sql          # PGvector + pg_trgm extensions
├── pom.xml                                # Parent Maven POM (Java 21, Spring Boot 3.5.x)
└── .env.example                           # Environment variable template
```

---

## Core Systems

### 1. Pipeline Processing System

**Location**: `agent-service/src/main/java/com/mrpot/agent/service/pipeline/`

The heart of the platform. All queries flow through a pipeline of sequential stages sharing a `PipelineContext`.

#### How It Works

1. Request arrives at `POST /answer/stream`.
2. `PipelineFactory` scores query complexity and creates a FAST or DEEP pipeline.
3. `PipelineRunner` executes stages sequentially, each implementing `Processor<I, O>`.
4. Each stage reads/writes to `PipelineContext.workingMemory` (a `ConcurrentHashMap`).
5. Stages emit `SseEnvelope` events streamed to the client in real-time.
6. For DEEP mode, a retry loop runs reasoning -> verification -> reflection until confidence is met or max rounds reached.

#### Key Classes

| Class | Purpose |
|---|---|
| `PipelineContext` | Central mutable state container (720 lines). Holds request, policy, SSE sequence counter, and typed accessors for all artifacts (plan, reasoning, verification, etc.) |
| `Processor<I, O>` | `@FunctionalInterface` -- all stages implement `Mono<O> process(I input, PipelineContext context)` |
| `PipelineFactory` | Creates FAST or DEEP pipeline based on complexity score (threshold: 0.6) |
| `PipelineRunner` | Executes pipeline stages, handles errors, emits telemetry |
| `DeepArtifactStore` | Typed wrapper around `PipelineContext` for DEEP artifacts: `setPlan()`, `getPlan()`, `setReasoningSteps()`, etc. |
| `DeepReasoningCoordinator` | Orchestrates multi-round reasoning. Returns `ReasoningResult` with rounds, hypothesis, confidence, status |
| `DeepModeConfig` | `@ConfigurationProperties("deep")` -- configurable parameters (see table below) |
| `SseEnvelope` | SSE event wrapper: `stage`, `message`, `payload` (Map), `seq`, `traceId`, `sessionId`, `ts` |

#### DEEP Mode Configuration

| Property | Default | Description |
|---|---|---|
| `deep.max-rounds-cap` | 5 | Maximum reasoning iterations |
| `deep.reasoning-timeout-seconds` | 120 | Total timeout for reasoning phase |
| `deep.confidence-threshold` | 0.85 | Confidence score to stop early |
| `deep.plan-timeout-seconds` | 30 | Timeout for plan generation |
| `deep.complexity-threshold` | 0.6 | Auto-triggers DEEP mode when exceeded |
| `deep.max-tool-rounds-cap` | 10 | Maximum tool call rounds |
| `deep.tool-timeout-seconds` | 30 | Individual tool call timeout |

---

### 2. Knowledge Base System

**Location**: `agent-knowledge/`

Manages document ingestion, storage, and vector retrieval.

#### Upload Workflow

```
Client                    agent-knowledge                 S3/Tigris
  │                            │                             │
  │ POST /kb/upload/presign    │                             │
  │───────────────────────────>│                             │
  │    { presignedUrl }        │                             │
  │<───────────────────────────│                             │
  │                            │                             │
  │  PUT presignedUrl (binary) │                             │
  │─────────────────────────────────────────────────────────>│
  │                            │                             │
  │ POST /kb/upload            │                             │
  │  { s3Url, docType }       │                             │
  │───────────────────────────>│                             │
  │                            │── Extract via vision AI ──> │
  │                            │── Generate embeddings ───>  │
  │                            │── Chunk content ─────────>  │
  │  { ChunkPreviewResponse }  │                             │
  │<───────────────────────────│                             │
  │                            │                             │
  │ POST /kb/upload/save       │                             │
  │  { chunks[] }             │                             │
  │───────────────────────────>│── Save to PostgreSQL ──>    │
  │   { savedIds[] }          │                             │
  │<───────────────────────────│                             │
```

#### Database Schema (`kb_documents`)

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated ID |
| `doc_type` | VARCHAR | Document classifier |
| `content` | TEXT | Full text content |
| `metadata` | JSONB | Flexible key-value storage |
| `embedding` | VECTOR(1536) | PGvector embedding (HNSW index, cosine distance) |

---

### 3. Tool Execution System

**Location**: `agent-tools-service/`

Executes specialized tools via MCP (Model Context Protocol). Called by `agent-service` during DEEP mode's tool orchestration stage.

#### Supported AI Providers

| Provider | Model | Use Case |
|---|---|---|
| **OpenAI** | gpt-4.1-mini | Chat completion |
| **OpenAI** | text-embedding-3-small | Embeddings (1536 dim) |
| **DeepSeek** | deepseek-chat | Primary LLM (temperature 0.2) |
| **Google Gemini** | gemini-2.0-flash | Alternative chat |
| **Alibaba DashScope** | qwen-vl-plus | Vision (file content extraction) |

#### Tool Categories

| Tool ID | Category | Description |
|---|---|---|
| `file.understandUrl` | File | Extract content from URLs via vision AI |
| `kb.search` | Knowledge | Vector search against KB |
| `kb.getDocument` | Knowledge | Retrieve document by ID |
| `reasoning.analyze` | Reasoning | Analyze reasoning artifacts |
| `reasoning.compare` | Reasoning | Compare reasoning results |
| `memory.store` | Memory | Store key-value in session memory |
| `memory.recall` | Memory | Recall stored memory |
| `verify.consistency` | Verification | Check logical consistency |
| `verify.fact_check` | Verification | Validate factual claims |
| `planning.decompose` | Planning | Break down complex tasks |
| `planning.next_step` | Planning | Determine next action |

#### Key Classes

| Class | Purpose |
|---|---|
| `ToolRegistry` | Discovers and registers all tool handlers at startup |
| `ToolHandler` | Base interface for tool implementations |
| `McpToolsController` | MCP protocol REST endpoints for tool invocation |
| `ToolInvoker` (in agent-service) | Client that calls tools via MCP. 30s timeout, 1 retry |

---

### 4. Telemetry System

**Location**: `agent-telemetry-service/`

Implements the **Transactional Outbox Pattern** for reliable event delivery:

```
agent-service                RabbitMQ              telemetry-service           PostgreSQL        Elasticsearch
     │                          │                        │                        │                    │
     │ publish event            │                        │                        │                    │
     │─────────────────────────>│                        │                        │                    │
     │                          │  @RabbitListener       │                        │                    │
     │                          │───────────────────────>│                        │                    │
     │                          │                        │  INSERT into outbox    │                    │
     │                          │                        │  (within transaction)  │                    │
     │                          │                        │───────────────────────>│                    │
     │                          │                        │  ACK message           │                    │
     │                          │<───────────────────────│                        │                    │
     │                          │                        │                        │                    │
     │                          │              EsOutboxWorker polls (5s)          │                    │
     │                          │                        │  SELECT 100 unprocessed│                    │
     │                          │                        │<───────────────────────│                    │
     │                          │                        │                   Bulk index               │
     │                          │                        │───────────────────────────────────────────>│
     │                          │                        │  Mark processed        │                    │
     │                          │                        │───────────────────────>│                    │
```

#### Configuration

| Parameter | Value | Description |
|---|---|---|
| Polling interval | 5s | How often outbox worker checks for new records |
| Batch size | 100 | Records per outbox poll |
| Max retries | 5 | Retry attempts on failure |
| Retry backoff | 2x | Exponential backoff multiplier |
| Retention | 7 days | Cleanup of processed outbox records |

#### Telemetry Events

| Event Type | Description |
|---|---|
| `run.start` | Agent run started |
| `run.rag_done` | RAG retrieval completed |
| `run.final` | Final answer generated |
| `run.failed` | Run failed with error |
| `run.cancelled` | Run cancelled |

---

### 5. Frontend

**Location**: `agent-knowledge-ui/`

React SPA with Firebase authentication and SSE streaming support.

#### Pages

| Page | Route | Description |
|---|---|---|
| **LoginPage** | `/login` | Firebase email/password + Google OAuth. Demo mode when Firebase is unavailable |
| **HomePage** | `/` | KB document browser with search (ILIKE), pagination (5/page), expandable rows, delete |
| **UploadPage** | `/upload` | File upload (presigned URL flow) or text input. Chunk preview before saving. Metadata JSON |
| **RunLogsDashboard** | `/telemetry` | Execution trace viewer. Session filtering, status badges, collapsible detail rows |

#### Key Infrastructure

| Component | Purpose |
|---|---|
| `AuthContext` | Global auth state via React Context. Provides `user`, `loading`, `isFirebaseConfigured`, auth methods. Falls back to demo mode with mock user |
| `ProtectedRoute` | Route guard. Redirects to `/login` if unauthenticated |
| `axios` instance | Pre-configured API client with base URL |
| Vite dev server | Proxy `/telemetry-api` to agent-telemetry-service |
| Docker (Nginx) | Multi-stage build: Node -> Nginx. `docker-entrypoint.sh` for dynamic config |

---

## Pipeline Stages

### RAG Pipeline (FAST Mode)

```
Request ──> HistoryStage ──> FileExtractStage ──> RagRetrieveStage ──> LlmStreamStage ──> ConversationSaveStage ──> TelemetryStage
```

| Stage | Input | Output | Description |
|---|---|---|---|
| **HistoryStage** | -- | Conversation history | Retrieves past Q&A from Redis. Formats as `U: question / A: answer`. Graceful on Redis failure |
| **FileExtractStage** | -- | Extracted files | Parallel extraction of uploaded file URLs via `file.understandUrl` tool |
| **RagRetrieveStage** | -- | KB documents | Semantic search via `KbRetrievalService`. Filters score >= 0.3, top 3 docs. Normalizes `【回答】` to `【QA】` |
| **LlmStreamStage** | -- | SSE stream | Constructs prompt with markers `【FILE】【KB】【HIS】【Q】`. Calls `LlmService.streamResponse()`. Strips markers from output |
| **ConversationSaveStage** | -- | -- | Persists Q&A pair to Redis for future context |
| **TelemetryStage** | -- | -- | Emits run telemetry events to RabbitMQ |

### DEEP Mode Pipeline

```
Request ──> HistoryStage ──> FileExtractStage ──> RagRetrieveStage
                                                       │
                                             ┌─────────┘
                                             v
                                       DeepPlanStage
                                             │
                                  ┌──────────┘
                                  v
                          ┌──> DeepReasoningStage
                          │         │
                   Retry  │         v
                   Loop   │  DeepToolOrchestrationStage
                   (max 5)│         │
                          │         v
                          │  DeepVerificationStage
                          │         │
                          │         v
                          │  DeepReflectionStage
                          │         │
                          └──── retry? ────┐
                                           │ proceed
                                           v
                                   DeepSynthesisStage ──> ConversationSaveStage ──> TelemetryStage
```

| Stage | Timeout | Description |
|---|---|---|
| **DeepPlanStage** | 30s | Generates structured plan with OBJECTIVE, SUBTASKS, SUCCESS_CRITERIA via LLM |
| **DeepReasoningStage** | 120s | Multi-round reasoning via `DeepReasoningCoordinator`. Produces hypothesis, evidence chains, confidence scores |
| **DeepToolOrchestrationStage** | 30s/tool | Executes tools based on plan subtasks. Max 3 tools per round. Records via `DeepToolAuditService` |
| **DeepVerificationStage** | -- | Runs `verify.consistency` and `verify.fact_check`. Produces `VerificationReport` with consistency score |
| **DeepReflectionStage** | -- | Analyzes report: consistency < 0.7 -> retry; unresolved claims > 2 -> retry; else proceed |
| **DeepSynthesisStage** | -- | Builds final answer from reasoning + evidence. Streams at ~80 chars/sec with CJK/English tokenization |

---

## Key Classes & Interfaces

### PipelineContext Working Memory Keys

| Key | Type | Set By | Used By |
|---|---|---|---|
| `extractedFiles` | `List<FileItem>` | FileExtractStage | LlmStreamStage |
| `ragDocs` | `List<KbDocument>` | RagRetrieveStage | LlmStreamStage |
| `ragHits` | `List<KbHit>` | RagRetrieveStage | TelemetryStage |
| `conversationHistory` | `String` | HistoryStage | LlmStreamStage |
| `finalAnswer` | `String` | LlmStreamStage / DeepSynthesisStage | ConversationSaveStage |
| `deepPlan` | `Map<String, Object>` | DeepPlanStage | DeepToolOrchestrationStage |
| `deepReasoning` | `Map<String, Object>` | DeepReasoningStage | DeepVerificationStage |
| `verificationReport` | `VerificationReport` | DeepVerificationStage | DeepReflectionStage |
| `needsAdditionalRound` | `Boolean` | DeepReflectionStage | DeepPipeline (retry loop) |
| `complexityScore` | `Double` | PipelineFactory | PipelineFactory (routing) |
| `toolCallHistory` | `List` | DeepToolOrchestrationStage | DeepVerificationStage |
| `toolEvidence` | `List` | DeepToolOrchestrationStage | DeepSynthesisStage |

### SSE Event Types (`StageNames`)

| Constant | Value | Emitted By |
|---|---|---|
| `ANSWER_DELTA` | `answer_delta` | LlmStreamStage, DeepSynthesisStage |
| `ANSWER_FINAL` | `answer_final` | Pipeline completion |
| `DEEP_PLAN_START` | `deep_plan_start` | DeepPlanStage |
| `DEEP_PLAN_DONE` | `deep_plan_done` | DeepPlanStage |
| `DEEP_REASONING_START` | `deep_reasoning_start` | DeepReasoningStage |
| `DEEP_REASONING_STEP` | `deep_reasoning_step` | DeepReasoningStage |
| `DEEP_REASONING_DONE` | `deep_reasoning_done` | DeepReasoningStage |
| `DEEP_TOOL_ORCH_START` | `deep_tool_orch_start` | DeepToolOrchestrationStage |
| `DEEP_TOOL_ORCH_RESULT` | `deep_tool_orch_result` | DeepToolOrchestrationStage |
| `DEEP_VERIFICATION` | `deep_verification` | DeepVerificationStage |
| `DEEP_REFLECTION` | `deep_reflection` | DeepReflectionStage |
| `DEEP_SYNTHESIS` | `deep_synthesis` | DeepSynthesisStage |
| `TOOL_CALL` | `tool_call` | Tool invocations |
| `ERROR` | `error` | Any stage on failure |

### Marker Patterns (LlmStreamStage)

The LLM prompt uses special markers to structure context:

| Marker | Purpose |
|---|---|
| `【FILE#N】` | Uploaded file content (numbered) |
| `【KB】` | Knowledge base documents |
| `【HIS】` | Conversation history |
| `【Q】` | User question |
| `【QA】` | Normalized answer prefix (stripped from output) |

---

## API Reference

### agent-service (Port 8080)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/answer/stream` | SSE streaming answer (RAG or DEEP). Body: `RagAnswerRequest` |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |

### agent-knowledge (Port 8083)

| Method | Endpoint | Description |
|---|---|---|
| GET | `/kb/documents` | List documents (paginated) |
| GET | `/kb/documents/search` | Fuzzy search (ILIKE) |
| GET | `/kb/documents/{id}` | Get document by ID |
| DELETE | `/kb/documents/{id}` | Delete document |
| POST | `/kb/upload/presign` | Generate presigned S3 URL |
| POST | `/kb/upload` | Process uploaded file (extract + chunk + embed) |
| POST | `/kb/upload/save` | Save previewed chunks to DB |

### agent-tools-service (Port 8081)

| Method | Endpoint | Description |
|---|---|---|
| GET | `/tools` | List all registered tools |
| POST | `/tools/{toolId}/invoke` | Invoke a specific tool |

### Swagger UI

| Service | URL |
|---|---|
| agent-service | http://localhost:8080/swagger-ui.html |
| agent-tools-service | http://localhost:8081/swagger-ui.html |
| agent-telemetry-service | http://localhost:8082/swagger-ui.html |
| agent-knowledge | http://localhost:8083/swagger-ui.html |

---

## Configuration

### Environment Variables

Copy `.env.example` and fill in API keys:

#### Required

| Variable | Description |
|---|---|
| `DEEPSEEK_API_KEY` | Primary LLM provider API key |
| `OPENAI_API_KEY` | OpenAI API key (embeddings + optional chat) |
| `DASHSCOPE_API_KEY` | Alibaba DashScope key (Qwen VL vision) |

#### Database & Infrastructure

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/aiagent` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `aiagent` | Database user |
| `DB_PASSWORD` | `aiagent_pwd` | Database password |
| `REDIS_URL` | `redis://localhost:6379` | Redis connection |
| `RABBITMQ_URL` | `amqp://aiagent:aiagent_pwd@localhost:5672` | RabbitMQ connection |
| `ELASTIC_BASE_URL` | `http://localhost:9200` | Elasticsearch URL |

#### AI Models

| Variable | Default | Description |
|---|---|---|
| `OPENAI_CHAT_MODEL` | `gpt-4.1-mini` | OpenAI chat model |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding model |
| `OPENAI_EMBEDDING_DIMENSIONS` | `1536` | Embedding dimensions |
| `DEEPSEEK_CHAT_MODEL` | `deepseek-chat` | DeepSeek model |
| `DEEPSEEK_TEMPERATURE` | `0.2` | DeepSeek temperature |
| `GEMINI_API_KEY` | -- | Google Gemini key (optional) |
| `GEMINI_CHAT_MODEL` | `gemini-2.0-flash` | Gemini model |
| `VISION_PROVIDER` | `qwen-vl` | File extraction provider (`qwen-vl` or `openai`) |

#### Storage

| Variable | Default | Description |
|---|---|---|
| `S3_ENDPOINT` | `https://t3.storageapi.dev` | S3-compatible endpoint |
| `S3_BUCKET` | -- | Bucket name |
| `S3_ACCESS_KEY` | -- | S3 access key |
| `S3_SECRET_KEY` | -- | S3 secret key |
| `S3_PRESIGN_TTL_SECONDS` | `900` | Presigned URL TTL (15 min) |

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 18+ (for frontend)
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d postgres redis rabbitmq elasticsearch
```

### 2. Configure Environment

```bash
cp .env.example .env
# Edit .env with your API keys (at minimum: DEEPSEEK_API_KEY, OPENAI_API_KEY)
```

### 3. Build & Run Backend

```bash
# Build all modules
./mvnw clean install

# Terminal 1: agent-service
cd agent-service && ../mvnw spring-boot:run

# Terminal 2: agent-tools-service
cd agent-tools-service && ../mvnw spring-boot:run

# Terminal 3: agent-knowledge
cd agent-knowledge && ../mvnw spring-boot:run

# Terminal 4: agent-telemetry-service
cd agent-telemetry-service && ../mvnw spring-boot:run
```

### 4. Run Frontend

```bash
cd agent-knowledge-ui
npm install
npm run dev
# Open http://localhost:3000
```

### 5. Run Tests

```bash
# All backend tests
./mvnw test

# Specific module
cd agent-service && ../mvnw test

# Frontend tests
cd agent-knowledge-ui && npm test
```

---

## Docker Deployment

### Full Stack (Infrastructure + Apps)

```bash
# Start everything
docker-compose --profile app up -d

# Infrastructure only (for local development)
docker-compose up -d
```

### Services Defined in `docker-compose.yml`

| Service | Image | Port(s) | Health Check |
|---|---|---|---|
| postgres | pgvector/pgvector:pg16 | 5432 | `pg_isready` |
| redis | redis:7.2-alpine | 6379 | `redis-cli ping` |
| rabbitmq | rabbitmq:3-management-alpine | 5672, 15672 | `rabbitmq-diagnostics ping` |
| elasticsearch | elasticsearch:8.13.4 | 9200, 9300 | `/_cluster/health` |
| kibana | kibana:8.13.4 | 5601 | -- |
| agent-service | Custom (Dockerfile) | 8080 | `/actuator/health` |
| agent-tools-service | Custom (Dockerfile) | 8081 | `/actuator/health` |
| agent-knowledge | Custom (Dockerfile) | 8083 | `/actuator/health` |
| agent-telemetry-service | Custom (Dockerfile) | 8082 | `/actuator/health` |
| prometheus | prom/prometheus:v2.51.0 | 9090 | `/-/healthy` |
| grafana | grafana/grafana:10.4.1 | 3000 | `/api/health` |

---

## Monitoring

### Prometheus

All backend services expose `/actuator/prometheus`. Scraped every 15s.

Metrics include: `up`, `http_server_requests_seconds_count`, `jvm_memory_used_bytes`, `hikaricp_connections`.

### Grafana

Auto-provisioned "AI Agent Platform Overview" dashboard. Access at http://localhost:3000 (admin/admin).

Tracks: service status, request rates, response times, JVM memory, DB connection pools.

### Kibana

Elasticsearch log exploration at http://localhost:5601. Index: `mrpot_candidates`.

### RabbitMQ Management

Queue monitoring at http://localhost:15672 (aiagent/aiagent_pwd).

---

## Technology Stack

| Category | Technology |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.5.x (WebFlux for agent-service, WebMVC for others) |
| **AI Framework** | Spring AI 1.1.2 |
| **Database** | PostgreSQL 16 + PGvector (HNSW, cosine distance, 1536 dim) |
| **Cache** | Redis 7.2 |
| **Message Queue** | RabbitMQ 3 (with DLQ) |
| **Search** | Elasticsearch 8.13.4 |
| **Frontend** | React 19 + Vite 6 + React Router 7 |
| **Auth** | Firebase (email/password + Google OAuth) |
| **Storage** | S3-compatible (Tigris) with presigned URLs |
| **AI Providers** | DeepSeek, OpenAI, Google Gemini, Alibaba DashScope |
| **API Docs** | SpringDoc OpenAPI 2.3 (Swagger UI) |
| **Monitoring** | Prometheus + Grafana |
| **Build** | Maven (multi-module), npm |
| **Containerization** | Docker + Docker Compose |

---

## Glossary

### Pipeline & Processing

| Term | Description |
|---|---|
| **PipelineContext** | Central mutable data store passed between stages. Contains request, policy, SSE counter, and `ConcurrentHashMap` working memory with typed accessors |
| **Processor\<I, O\>** | `@FunctionalInterface` base interface. `Mono<O> process(I input, PipelineContext context)`. All stages implement this |
| **SseEnvelope** | SSE event wrapper: `stage`, `message`, `payload` (Map), `seq`, `traceId`, `sessionId`, `ts` |
| **DeepArtifactStore** | Typed wrapper around `PipelineContext` for DEEP mode artifacts |
| **DeepReasoningCoordinator** | Orchestrates multi-round reasoning with hypothesis, evidence, and confidence tracking |
| **ExecutionPolicy** | Defines pipeline parameters: `maxToolRounds`, `reasoningTimeoutSeconds`, etc. |
| **StageNames** | Constants for SSE event types (`ANSWER_DELTA`, `DEEP_PLAN_DONE`, `ERROR`, etc.) |

### Processing Modes

| Term | Description |
|---|---|
| **FAST (RAG)** | Standard mode. 4 stages: History -> Retrieve -> Stream -> Save. Triggered when complexity <= 0.6 |
| **DEEP Mode** | Advanced reasoning. 6 extra stages with retry loop. Triggered when complexity > 0.6 |
| **Complexity Score** | 0.0 - 1.0 score computed from query features. Determines FAST vs DEEP routing |

### Knowledge Base

| Term | Description |
|---|---|
| **KbDocument** | A knowledge base entry with `docType`, `content`, `metadata` (JSONB), `embedding` (VECTOR) |
| **KbHit** | Search result with document reference and relevance score |
| **ChunkPreviewResponse** | Returned after upload processing. Contains chunks with content, embedding, metadata. User reviews before saving |
| **PGvector** | PostgreSQL extension for vector storage. HNSW index, cosine distance, 1536 dimensions |

### Tools & MCP

| Term | Description |
|---|---|
| **MCP (Model Context Protocol)** | Protocol for tool invocation. `agent-service` calls `agent-tools-service` via REST |
| **ToolInvoker** | Client in `agent-service` that calls tools. 30s timeout, 1 retry |
| **ToolRegistry** | Auto-discovers and registers all `ToolHandler` implementations at startup |
| **ToolHandler** | Interface for tool implementations. Each tool has an ID, schema, and execute method |

### Telemetry

| Term | Description |
|---|---|
| **Transactional Outbox** | Pattern: write to outbox table within DB transaction, then async bulk-index to Elasticsearch |
| **EsOutboxWorker** | Background worker polling PostgreSQL every 5s, batching 100 records to Elasticsearch |
| **DLQ (Dead Letter Queue)** | RabbitMQ queue for failed messages. Managed via `DlqController` |

### Frontend

| Term | Description |
|---|---|
| **AuthContext** | React Context providing `user`, `loading`, auth methods. Demo mode fallback when Firebase unavailable |
| **ProtectedRoute** | Route guard component. Checks auth, redirects to `/login` if unauthenticated |

### Marker Patterns

| Term | Description |
|---|---|
| **【FILE#N】** | Marker for file content in LLM prompt. Numbered per file |
| **【KB】** | Marker for knowledge base context |
| **【HIS】** | Marker for conversation history |
| **【Q】** | Marker for user question |
| **【QA】** | Normalized answer marker. Stripped from LLM output |

---

## Design Patterns

| Pattern | Where Used | Description |
|---|---|---|
| **Pipeline** | agent-service | Sequential stage processing with shared context |
| **Transactional Outbox** | agent-telemetry-service | Reliable event delivery despite downstream failures |
| **Presigned URLs** | agent-knowledge | Direct client-to-S3 uploads without server bottleneck |
| **Marker-Based Prompting** | LlmStreamStage | Special markers structure LLM context sections |
| **SSE Streaming** | AnswerStreamController | Real-time progress feedback and incremental answer delivery |
| **Multi-Provider Strategy** | agent-tools-service | Abstracts AI providers for flexibility and failover |
| **Iterative Refinement** | DeepPipeline | Retry loop: reasoning -> verification -> reflection until confidence met |
| **MCP Protocol** | Tool invocation | Standardized tool discovery, schema, and execution |

---

## License

MIT
