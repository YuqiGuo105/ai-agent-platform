import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import './PortfolioPage.css';

const architectureDiagram = `
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
           ├───────────>│ agent-tools-service  │            │
           │  MCP calls │  Tool Execution      │            │
           │            │   (Port 8081)        │            │
           │            └──────────────────────┘            │
           │                                                │
           │                  RabbitMQ                       │
           └────────────────────────────────────────────────┘
                          (Telemetry Events)

     ┌──────────┐    ┌──────────┐    ┌───────────────┐    ┌──────────┐
     │PostgreSQL│    │  Redis   │    │ Elasticsearch │    │ S3/Tigris│
     │+ PGvector│    │  Cache   │    │  (Telemetry)  │    │ (Files)  │
     └──────────┘    └──────────┘    └───────────────┘    └──────────┘
`;

const services = [
  { name: 'agent-service', port: 8080, framework: 'Spring WebFlux', role: 'Core pipeline orchestrator. Handles RAG and DEEP processing, SSE streaming.', prodUrl: 'https://agent-service-production-d62b.up.railway.app' },
  { name: 'agent-tools-service', port: 8081, framework: 'Spring WebMVC', role: 'Tool execution engine. Integrates with multiple AI providers via MCP protocol.', prodUrl: 'https://agent-tools-service-production.up.railway.app' },
  { name: 'agent-telemetry-service', port: 8082, framework: 'Spring WebMVC', role: 'Asynchronous event processing. Transactional outbox pattern to Elasticsearch.', prodUrl: 'https://agent-telemetry-service-production.up.railway.app' },
  { name: 'agent-knowledge', port: 8083, framework: 'Spring WebMVC', role: 'Knowledge base CRUD, vector search, document upload with presigned URLs.', prodUrl: 'https://agent-knowledge-production.up.railway.app' },
  { name: 'agent-knowledge-ui', port: 3000, framework: 'React + Vite', role: 'Web interface. Firebase auth, KB browser, upload UI, telemetry dashboard.', prodUrl: 'https://agent-knowledge-production.up.railway.app' },
  { name: 'agent-common', port: '-', framework: 'Library', role: 'Shared models, DTOs, SSE stage constants.', prodUrl: '-' },
];

const infraComponents = [
  { name: 'PostgreSQL 16 (PGvector)', purpose: 'Primary database. Stores KB documents with vector embeddings (1536-dim HNSW index).' },
  { name: 'Redis 7', purpose: 'Conversation history cache, session storage.' },
  { name: 'RabbitMQ 3', purpose: 'Async telemetry event delivery (with DLQ).' },
  { name: 'Elasticsearch 8', purpose: 'Telemetry event indexing and search.' },
  { name: 'Prometheus', purpose: 'Metrics scraping (15s interval).' },
  { name: 'Grafana', purpose: 'Dashboard visualization.' },
  { name: 'Kibana', purpose: 'Elasticsearch log exploration.' },
];

const pipelineConfig = [
  { prop: 'deep.max-rounds-cap', def: '5', desc: 'Maximum reasoning iterations.' },
  { prop: 'deep.reasoning-timeout-seconds', def: '120', desc: 'Total timeout for reasoning phase.' },
  { prop: 'deep.confidence-threshold', def: '0.85', desc: 'Confidence score to stop early.' },
  { prop: 'deep.plan-timeout-seconds', def: '30', desc: 'Timeout for plan generation.' },
  { prop: 'deep.complexity-threshold', def: '0.6', desc: 'Auto-triggers DEEP mode when exceeded.' },
  { prop: 'deep.max-tool-rounds-cap', def: '10', desc: 'Maximum tool call rounds.' },
  { prop: 'deep.tool-timeout-seconds', def: '30', desc: 'Individual tool call timeout.' },
];

const techStack = [
  { category: 'Language', tech: 'Java 21' },
  { category: 'Framework', tech: 'Spring Boot 3.5.x (WebFlux + WebMVC)' },
  { category: 'AI Framework', tech: 'Spring AI 1.1.2' },
  { category: 'Database', tech: 'PostgreSQL 16 + PGvector (HNSW, cosine, 1536 dim)' },
  { category: 'Cache', tech: 'Redis 7.2' },
  { category: 'Message Queue', tech: 'RabbitMQ 3 (with DLQ)' },
  { category: 'Search', tech: 'Elasticsearch 8.13.4' },
  { category: 'Frontend', tech: 'React + Vite + React Router' },
  { category: 'Auth', tech: 'Firebase (email/password + Google OAuth)' },
  { category: 'Storage', tech: 'S3-compatible (Tigris) with presigned URLs' },
  { category: 'AI Providers', tech: 'DeepSeek, OpenAI, Google Gemini, Alibaba DashScope' },
  { category: 'Monitoring', tech: 'Prometheus + Grafana' },
  { category: 'Containerization', tech: 'Docker + Docker Compose' },
];

const requiredEnv = [
  { variable: 'DEEPSEEK_API_KEY', description: 'Primary LLM provider API key.' },
  { variable: 'OPENAI_API_KEY', description: 'OpenAI API key (embeddings + optional chat).' },
  { variable: 'DASHSCOPE_API_KEY', description: 'Alibaba DashScope key (Qwen VL vision).' },
];

const infraEnv = [
  { variable: 'DB_URL', def: 'jdbc:postgresql://localhost:5432/aiagent', description: 'PostgreSQL JDBC URL.' },
  { variable: 'DB_USERNAME', def: 'aiagent', description: 'Database user.' },
  { variable: 'DB_PASSWORD', def: 'aiagent_pwd', description: 'Database password.' },
  { variable: 'REDIS_URL', def: 'redis://localhost:6379', description: 'Redis connection URL.' },
  { variable: 'RABBITMQ_URL', def: 'amqp://aiagent:aiagent_pwd@localhost:5672', description: 'RabbitMQ connection URL.' },
  { variable: 'ELASTIC_BASE_URL', def: 'http://localhost:9200', description: 'Elasticsearch base URL.' },
];

const aiEnv = [
  { variable: 'OPENAI_CHAT_MODEL', def: 'gpt-4.1-mini', description: 'OpenAI chat model.' },
  { variable: 'OPENAI_EMBEDDING_MODEL', def: 'text-embedding-3-small', description: 'Embedding model.' },
  { variable: 'OPENAI_EMBEDDING_DIMENSIONS', def: '1536', description: 'Embedding dimensions.' },
  { variable: 'DEEPSEEK_CHAT_MODEL', def: 'deepseek-chat', description: 'DeepSeek model name.' },
  { variable: 'DEEPSEEK_TEMPERATURE', def: '0.2', description: 'DeepSeek temperature.' },
  { variable: 'GEMINI_API_KEY', def: '-', description: 'Google Gemini API key (optional).' },
  { variable: 'GEMINI_CHAT_MODEL', def: 'gemini-2.0-flash', description: 'Gemini model.' },
  { variable: 'VISION_PROVIDER', def: 'qwen-vl', description: 'File extraction provider (`qwen-vl` or `openai`).' },
];

const storageEnv = [
  { variable: 'S3_ENDPOINT', def: 'https://t3.storageapi.dev', description: 'S3-compatible endpoint.' },
  { variable: 'S3_BUCKET', def: '-', description: 'Bucket name.' },
  { variable: 'S3_ACCESS_KEY', def: '-', description: 'S3 access key.' },
  { variable: 'S3_SECRET_KEY', def: '-', description: 'S3 secret key.' },
  { variable: 'S3_PRESIGN_TTL_SECONDS', def: '900', description: 'Presigned URL TTL (seconds).' },
];

export default function PortfolioPage() {
  const { user, logout } = useAuth();
  const [activeTab, setActiveTab] = useState('overview');

  function toggleSystem(id) {
    setExpandedSystems(prev =>
      prev.includes(id) ? prev.filter(s => s !== id) : [...prev, id]
    );
  }

  function getAvatarUrl(url) {
    if (!url) return null;
    if (url.includes('googleusercontent.com') && !url.includes('sz=')) {
      return url + (url.includes('?') ? '&' : '?') + 'sz=64';
    }
    return url;
  }

  function getInitials(name, email) {
    if (name) return name.split(' ').map(n => n[0]).join('').toUpperCase();
    if (email) return email[0].toUpperCase();
    return '?';
  }

  return (
    <div className="portfolio-page">
      <nav className="topbar">
        <h1 className="topbar-title">Agent Knowledge</h1>
        <div className="topbar-right">
          <span className="user-label">
            {user?.displayName || user?.email || 'User'}
          </span>
          {user?.photoURL ? (
            <img
              src={getAvatarUrl(user.photoURL)}
              alt="avatar"
              className="user-avatar"
              onError={e => {
                e.target.onerror = null;
                e.target.style.display = 'none';
                e.target.nextSibling.style.display = 'flex';
              }}
            />
          ) : null}
          <span
            className="user-avatar-fallback"
            style={{
              display: user?.photoURL ? 'none' : 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 32,
              height: 32,
              borderRadius: '50%',
              background: '#dee2e6',
              color: '#495057',
              fontWeight: 600,
              fontSize: 14,
            }}
          >
            {getInitials(user?.displayName, user?.email)}
          </span>
          <Link to="/" className="btn-nav" title="Home">
            🏠 Home
          </Link>
          <button className="btn-logout" onClick={logout}>
            Sign Out
          </button>
        </div>
      </nav>

      <main className="portfolio-main">
        <section className="portfolio-hero">
          <div className="hero-content">
            <h1>AI Agent Platform</h1>
            <p className="hero-subtitle">
              A microservices-based AI question-answering platform with two processing modes: <strong>RAG (FAST)</strong> and <strong>DEEP</strong>.
            </p>
            <div className="hero-features">
              <span className="feature-badge">🤖 RAG &amp; DEEP Pipelines</span>
              <span className="feature-badge">🧠 Multi-provider AI Tools</span>
              <span className="feature-badge">📈 Telemetry &amp; Monitoring</span>
              <span className="feature-badge">📚 Knowledge Base &amp; UI</span>
            </div>
          </div>
        </section>

        <div className="tab-nav">
          {[
            { id: 'overview', label: '📋 Overview' },
            { id: 'architecture', label: '🏗️ Architecture' },
            { id: 'core', label: '⚙️ Core Systems' },
            { id: 'pipeline', label: '🧬 Pipelines' },
            { id: 'api', label: '🔌 APIs & Config' },
            { id: 'getting-started', label: '🚀 Getting Started' },
            { id: 'monitoring', label: '📊 Monitoring' },
            { id: 'tech', label: '💻 Tech Stack' },
            { id: 'glossary', label: '📖 Glossary' },
          ].map(tab => (
            <button
              key={tab.id}
              className={`tab-btn ${activeTab === tab.id ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {activeTab === 'overview' && (
          <section className="portfolio-section">
            <h2>Overview</h2>
            <p className="section-intro">
              The platform enables users to ask questions and receive AI-powered answers augmented by a knowledge base. It supports
              two processing modes selected by query complexity scoring.
            </p>

            <h3>Processing Modes</h3>
            <div className="api-table-wrap">
              <table className="api-table">
                <thead>
                  <tr>
                    <th>Mode</th>
                    <th>Trigger</th>
                    <th>Pipeline Stages</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><strong>FAST (RAG)</strong></td>
                    <td>Complexity ≤ 0.6</td>
                    <td>4 stages</td>
                    <td>Retrieve relevant docs, stream LLM answer, save conversation.</td>
                  </tr>
                  <tr>
                    <td><strong>DEEP</strong></td>
                    <td>Complexity &gt; 0.6</td>
                    <td>6+ stages</td>
                    <td>Plan, multi-round reasoning, tool orchestration, verification, reflection, synthesis.</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <h3 style={{ marginTop: '2rem' }}>Target Users</h3>
            <div className="overview-grid">
              <div className="audience-card">
                <h3>🙋 End Users</h3>
                <ul>
                  <li>Ask questions via SSE streaming.</li>
                  <li>Upload files for additional context.</li>
                  <li>Receive RAG or DEEP answers based on complexity.</li>
                </ul>
              </div>
              <div className="audience-card">
                <h3>📚 Knowledge Managers</h3>
                <ul>
                  <li>Upload documents and manage the knowledge base via UI.</li>
                  <li>Preview chunks before saving to the database.</li>
                </ul>
              </div>
              <div className="audience-card">
                <h3>🛠️ System Operators</h3>
                <ul>
                  <li>Monitor health via Grafana/Prometheus.</li>
                  <li>View execution traces in telemetry dashboard.</li>
                  <li>Manage DLQ messages.</li>
                </ul>
              </div>
            </div>
          </section>
        )}

        {activeTab === 'architecture' && (
          <section className="portfolio-section">
            <h2>Architecture</h2>
            <div className="architecture-diagram">
              <pre>{architectureDiagram}</pre>
            </div>

            <h3>Services</h3>
            <div className="api-table-wrap">
              <table className="api-table">
                <thead>
                  <tr>
                    <th>Service</th>
                    <th>Port</th>
                    <th>Framework</th>
                    <th>Production URL</th>
                  </tr>
                </thead>
                <tbody>
                  {services.map(s => (
                    <tr key={s.name}>
                      <td><code>{s.name}</code></td>
                      <td>{s.port}</td>
                      <td>{s.framework}</td>
                      <td>{s.prodUrl !== '-' ? <a href={s.prodUrl} target="_blank" rel="noopener noreferrer">{s.prodUrl.replace('https://', '')}</a> : '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <h3 style={{ marginTop: '2rem' }}>Infrastructure</h3>
            <div className="api-table-wrap">
              <table className="api-table">
                <thead>
                  <tr>
                    <th>Component</th>
                    <th>Purpose</th>
                  </tr>
                </thead>
                <tbody>
                  {infraComponents.map(c => (
                    <tr key={c.name}>
                      <td>{c.name}</td>
                      <td>{c.purpose}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}

        {activeTab === 'core' && (
          <section className="portfolio-section">
            <h2>Core Systems</h2>

            <h3>1. Pipeline Processing System</h3>
            <p>
              Location: <code>agent-service/src/main/java/com/mrpot/agent/service/pipeline/</code>.
              All queries flow through a pipeline of sequential stages sharing a <code>PipelineContext</code>.
            </p>
            <ul>
              <li><strong>PipelineFactory</strong> scores query complexity and chooses FAST vs DEEP.</li>
              <li><strong>PipelineRunner</strong> executes <code>Processor&lt;I, O&gt;</code> stages sequentially.</li>
              <li>Each stage reads/writes to <code>PipelineContext.workingMemory</code> and can emit SSE events.</li>
            </ul>

            <div className="system-table-wrap" style={{ marginTop: '1rem' }}>
              <h4>Key Classes</h4>
              <table className="system-table">
                <thead>
                  <tr>
                    <th>Class</th>
                    <th>Purpose</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><code>PipelineContext</code></td>
                    <td>Central mutable state container (request, policy, SSE counter, working memory accessors).</td>
                  </tr>
                  <tr>
                    <td><code>Processor&lt;I, O&gt;</code></td>
                    <td>Stage interface: <code>Mono&lt;O&gt; process(I input, PipelineContext ctx)</code>.</td>
                  </tr>
                  <tr>
                    <td><code>PipelineFactory</code></td>
                    <td>Builds FAST or DEEP pipelines based on complexity threshold (0.6).</td>
                  </tr>
                  <tr>
                    <td><code>DeepArtifactStore</code></td>
                    <td>Typed wrapper around <code>PipelineContext</code> for DEEP artifacts.</td>
                  </tr>
                  <tr>
                    <td><code>DeepReasoningCoordinator</code></td>
                    <td>Orchestrates multi-round reasoning, returns hypothesis and confidence.</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div className="system-table-wrap" style={{ marginTop: '1.5rem' }}>
              <h4>DEEP Mode Configuration</h4>
              <table className="system-table">
                <thead>
                  <tr>
                    <th>Property</th>
                    <th>Default</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {pipelineConfig.map(c => (
                    <tr key={c.prop}>
                      <td><code>{c.prop}</code></td>
                      <td>{c.def}</td>
                      <td>{c.desc}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <h3 style={{ marginTop: '2rem' }}>2. Knowledge Base System</h3>
            <p>
              Location: <code>agent-knowledge/</code>. Manages document ingestion, storage, and vector retrieval using PostgreSQL + PGvector.
            </p>

            <h4>Upload Workflow</h4>
            <pre className="code-block" style={{ marginBottom: '1rem' }}>{`Client                    agent-knowledge                 S3/Tigris
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
  │  { s3Url, docType }        │                             │
  │───────────────────────────>│                             │
  │                            │── Extract via vision AI ──> │
  │                            │── Generate embeddings ───>  │
  │                            │── Chunk content ─────────>  │
  │  { ChunkPreviewResponse }  │                             │
  │<───────────────────────────│                             │
  │                            │                             │
  │ POST /kb/upload/save       │                             │
  │  { chunks[] }              │                             │
  │───────────────────────────>│── Save to PostgreSQL ──>    │
  │   { savedIds[] }           │                             │
  │<───────────────────────────│                             │`}</pre>

            <h4>kb_documents Schema (simplified)</h4>
            <div className="system-table-wrap">
              <table className="system-table">
                <thead>
                  <tr>
                    <th>Column</th>
                    <th>Type</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>id</td>
                    <td>BIGINT PK</td>
                    <td>Auto-generated ID.</td>
                  </tr>
                  <tr>
                    <td>doc_type</td>
                    <td>VARCHAR</td>
                    <td>Document classifier.</td>
                  </tr>
                  <tr>
                    <td>content</td>
                    <td>TEXT</td>
                    <td>Full text content.</td>
                  </tr>
                  <tr>
                    <td>metadata</td>
                    <td>JSONB</td>
                    <td>Flexible key-value metadata.</td>
                  </tr>
                  <tr>
                    <td>embedding</td>
                    <td>VECTOR(1536)</td>
                    <td>PGvector embedding (HNSW index, cosine distance).</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <h3 style={{ marginTop: '2rem' }}>3. Tool Execution System</h3>
            <p>
              Location: <code>agent-tools-service/</code>. Executes specialized tools via MCP. Called by agent-service during DEEP mode tool
              orchestration.
            </p>
            <ul>
              <li>Supports DeepSeek, OpenAI, Google Gemini, Alibaba DashScope.</li>
              <li>Tool categories: file, knowledge, reasoning, memory, verification, planning.</li>
            </ul>

            <h3 style={{ marginTop: '2rem' }}>4. Telemetry System</h3>
            <p>
              Location: <code>agent-telemetry-service/</code>. Implements the Transactional Outbox Pattern for reliable event delivery to Elasticsearch.
            </p>

            <pre className="code-block" style={{ marginBottom: '1rem' }}>{`agent-service                RabbitMQ              telemetry-service           PostgreSQL        Elasticsearch
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
     │                          │                        │───────────────────────>│                    │`}</pre>

            <h3 style={{ marginTop: '2rem' }}>5. Frontend</h3>
            <p>
              Location: <code>agent-knowledge-ui/</code>. React SPA with Firebase authentication and SSE-based UX for pipelines and telemetry.
            </p>
          </section>
        )}

        {activeTab === 'pipeline' && (
          <section className="portfolio-section">
            <h2>Pipeline Stages</h2>

            <h3>RAG Pipeline (FAST Mode)</h3>
            <pre className="code-block" style={{ marginBottom: '1rem' }}>{`Request ──> HistoryStage ──> FileExtractStage ──> RagRetrieveStage ──> LlmStreamStage ──> ConversationSaveStage ──> TelemetryStage`}</pre>

            <div className="system-table-wrap">
              <table className="system-table">
                <thead>
                  <tr>
                    <th>Stage</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>HistoryStage</td>
                    <td>Fetches conversation history from Redis, formats as <code>U:/A:</code> pairs.</td>
                  </tr>
                  <tr>
                    <td>FileExtractStage</td>
                    <td>Extracts content from uploaded file URLs via <code>file.understandUrl</code> tool.</td>
                  </tr>
                  <tr>
                    <td>RagRetrieveStage</td>
                    <td>Semantic KB search, filters score ≥ 0.3, returns top 3 docs.</td>
                  </tr>
                  <tr>
                    <td>LlmStreamStage</td>
                    <td>Constructs marker-based prompt and streams answer via LLM.</td>
                  </tr>
                  <tr>
                    <td>ConversationSaveStage</td>
                    <td>Persists Q&amp;A pair to Redis.</td>
                  </tr>
                  <tr>
                    <td>TelemetryStage</td>
                    <td>Emits telemetry events to RabbitMQ.</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <h3 style={{ marginTop: '2rem' }}>DEEP Mode Pipeline</h3>
            <pre className="code-block" style={{ marginBottom: '1rem' }}>{`Request ──> HistoryStage ──> FileExtractStage ──> RagRetrieveStage
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
                                   DeepSynthesisStage ──> ConversationSaveStage ──> TelemetryStage`}</pre>
          </section>
        )}

        {activeTab === 'api' && (
          <section className="portfolio-section">
            <h2>APIs & Configuration</h2>

            <h3>Swagger UI (API Documentation)</h3>
            <p className="section-intro">Each backend service exposes OpenAPI documentation via Swagger UI at <code>/swagger-ui.html</code>.</p>
            <div className="api-table-wrap">
              <table className="api-table">
                <thead>
                  <tr>
                    <th>Service</th>
                    <th>Production Swagger UI</th>
                    <th>Local URL</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>agent-service</td>
                    <td><a href="https://agent-service-production-d62b.up.railway.app/swagger-ui.html" target="_blank" rel="noopener noreferrer">agent-service-production-d62b.up.railway.app/swagger-ui.html</a></td>
                    <td><code>http://localhost:8080/swagger-ui.html</code></td>
                  </tr>
                  <tr>
                    <td>agent-tools-service</td>
                    <td><a href="https://agent-tools-service-production.up.railway.app/swagger-ui.html" target="_blank" rel="noopener noreferrer">agent-tools-service-production.up.railway.app/swagger-ui.html</a></td>
                    <td><code>http://localhost:8081/swagger-ui.html</code></td>
                  </tr>
                  <tr>
                    <td>agent-telemetry-service</td>
                    <td><a href="https://agent-telemetry-service-production.up.railway.app/swagger-ui.html" target="_blank" rel="noopener noreferrer">agent-telemetry-service-production.up.railway.app/swagger-ui.html</a></td>
                    <td><code>http://localhost:8082/swagger-ui.html</code></td>
                  </tr>
                  <tr>
                    <td>agent-knowledge</td>
                    <td><a href="https://agent-knowledge-production.up.railway.app/swagger-ui.html" target="_blank" rel="noopener noreferrer">agent-knowledge-production.up.railway.app/swagger-ui.html</a></td>
                    <td><code>http://localhost:8083/swagger-ui.html</code></td>
                  </tr>
                </tbody>
              </table>
            </div>

            <h3 style={{ marginTop: '2rem' }}>Key Endpoints</h3>
            <div className="api-table-wrap">
              <table className="api-table">
                <thead>
                  <tr>
                    <th>Service</th>
                    <th>Method</th>
                    <th>Endpoint</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>agent-service</td>
                    <td><span className="method-badge method-post">POST</span></td>
                    <td><code>/answer/stream</code></td>
                    <td>SSE streaming answer (RAG or DEEP). Body: <code>RagAnswerRequest</code>.</td>
                  </tr>
                  <tr>
                    <td>agent-knowledge</td>
                    <td><span className="method-badge method-get">GET</span></td>
                    <td><code>/kb/documents/search</code></td>
                    <td>Fuzzy document search.</td>
                  </tr>
                  <tr>
                    <td>agent-tools-service</td>
                    <td><span className="method-badge method-post">POST</span></td>
                    <td><code>/tools/{toolId}/invoke</code></td>
                    <td>Invoke MCP tool by ID.</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <h3 style={{ marginTop: '2rem' }}>API Structure (by Service)</h3>
            <pre className="code-block">{`agent-service (core pipeline)
  /answer/...           # Question-answering APIs (RAG & DEEP)
  /actuator/...         # Health and metrics (Prometheus at /actuator/prometheus)
  /swagger-ui.html      # Swagger UI
  /v3/api-docs          # OpenAPI JSON

agent-knowledge (knowledge base)
  /kb/documents         # List documents
  /kb/documents/search  # Search documents
  /kb/upload            # Create chunk preview from uploaded file
  /kb/upload/save       # Persist approved chunks to PostgreSQL
  /actuator/...         # Health and metrics
  /swagger-ui.html      # Swagger UI

agent-tools-service (tools)
  /tools/{toolId}/invoke  # Invoke a specific MCP tool
  /actuator/...           # Health and metrics
  /swagger-ui.html        # Swagger UI

agent-telemetry-service (telemetry)
  /actuator/...           # Health and metrics
  /swagger-ui.html        # Swagger UI
  (Elasticsearch indexing happens via background workers, not a public HTTP API)
`}</pre>

            <h3 style={{ marginTop: '2rem' }}>Environment Variables</h3>
            <h4>Required</h4>
            <div className="env-table-wrap">
              <table className="env-table">
                <thead>
                  <tr>
                    <th>Variable</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {requiredEnv.map(e => (
                    <tr key={e.variable}>
                      <td><code>{e.variable}</code></td>
                      <td>{e.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <h4 style={{ marginTop: '1.5rem' }}>Database & Infrastructure</h4>
            <div className="env-table-wrap">
              <table className="env-table">
                <thead>
                  <tr>
                    <th>Variable</th>
                    <th>Default</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {infraEnv.map(e => (
                    <tr key={e.variable}>
                      <td><code>{e.variable}</code></td>
                      <td>{e.def}</td>
                      <td>{e.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <h4 style={{ marginTop: '1.5rem' }}>AI Models</h4>
            <div className="env-table-wrap">
              <table className="env-table">
                <thead>
                  <tr>
                    <th>Variable</th>
                    <th>Default</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {aiEnv.map(e => (
                    <tr key={e.variable}>
                      <td><code>{e.variable}</code></td>
                      <td>{e.def}</td>
                      <td>{e.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <h4 style={{ marginTop: '1.5rem' }}>Storage</h4>
            <div className="env-table-wrap">
              <table className="env-table">
                <thead>
                  <tr>
                    <th>Variable</th>
                    <th>Default</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {storageEnv.map(e => (
                    <tr key={e.variable}>
                      <td><code>{e.variable}</code></td>
                      <td>{e.def}</td>
                      <td>{e.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}

        {activeTab === 'getting-started' && (
          <section className="portfolio-section">
            <h2>Getting Started</h2>

            <div className="setup-steps">
              <div className="setup-step">
                <span className="step-number">1</span>
                <div className="step-content">
                  <h4>Prerequisites</h4>
                  <ul>
                    <li>Java 21+</li>
                    <li>Maven 3.9+</li>
                    <li>Node.js 18+ (for frontend)</li>
                    <li>Docker &amp; Docker Compose</li>
                  </ul>
                </div>
              </div>

              <div className="setup-step">
                <span className="step-number">2</span>
                <div className="step-content">
                  <h4>Start Infrastructure</h4>
                  <pre className="code-block">{`docker-compose up -d postgres redis rabbitmq elasticsearch`}</pre>
                </div>
              </div>

              <div className="setup-step">
                <span className="step-number">3</span>
                <div className="step-content">
                  <h4>Configure Environment</h4>
                  <pre className="code-block">{`cp .env.example .env
# Edit .env with your API keys (at minimum: DEEPSEEK_API_KEY, OPENAI_API_KEY)`}</pre>
                </div>
              </div>

              <div className="setup-step">
                <span className="step-number">4</span>
                <div className="step-content">
                  <h4>Build &amp; Run Backend</h4>
                  <pre className="code-block">{`# Build all modules
./mvnw clean install

# In separate terminals:
cd agent-service && ../mvnw spring-boot:run
cd agent-tools-service && ../mvnw spring-boot:run
cd agent-knowledge && ../mvnw spring-boot:run
cd agent-telemetry-service && ../mvnw spring-boot:run`}</pre>
                </div>
              </div>

              <div className="setup-step">
                <span className="step-number">5</span>
                <div className="step-content">
                  <h4>Run Frontend</h4>
                  <pre className="code-block">{`cd agent-knowledge-ui
npm install
npm run dev
# Open http://localhost:3000`}</pre>
                </div>
              </div>
            </div>
          </section>
        )}

        {activeTab === 'monitoring' && (
          <section className="portfolio-section">
            <h2>Monitoring</h2>

            <h3>Prometheus</h3>
            <p>All backend services expose <code>/actuator/prometheus</code> and are scraped every 15s.</p>

            <h3 style={{ marginTop: '1.5rem' }}>Grafana</h3>
            <p>
              Pre-provisioned dashboards in <code>grafana/provisioning/</code>. Default URL: <code>http://localhost:3000</code> (admin/admin).
            </p>

            <h3 style={{ marginTop: '1.5rem' }}>Kibana</h3>
            <p>Elasticsearch log exploration at <code>http://localhost:5601</code>.</p>

            <h3 style={{ marginTop: '1.5rem' }}>RabbitMQ Management</h3>
            <p>Queue monitoring at <code>http://localhost:15672</code> (aiagent/aiagent_pwd).</p>
          </section>
        )}

        {activeTab === 'tech' && (
          <section className="portfolio-section">
            <h2>Technology Stack</h2>
            <div className="tech-grid">
              {techStack.map(item => (
                <div key={item.category + item.tech} className="tech-card">
                  <span className="tech-category">{item.category}</span>
                  <span className="tech-name">{item.tech}</span>
                </div>
              ))}
            </div>
          </section>
        )}

        {activeTab === 'glossary' && (
          <section className="portfolio-section">
            <h2>Glossary & Patterns</h2>

            <h3>Pipeline & Processing</h3>
            <ul>
              <li><strong>PipelineContext</strong> – Shared mutable data passed between stages.</li>
              <li><strong>Processor&lt;I, O&gt;</strong> – Functional stage interface used by all pipeline stages.</li>
              <li><strong>SseEnvelope</strong> – Wrapper for SSE events (stage, message, payload, seq, traceId, sessionId, ts).</li>
            </ul>

            <h3 style={{ marginTop: '1.5rem' }}>Processing Modes</h3>
            <ul>
              <li><strong>FAST (RAG)</strong> – 4-stage pipeline; used for simple queries.</li>
              <li><strong>DEEP Mode</strong> – Multi-stage pipeline with planning, tools, and verification.</li>
              <li><strong>Complexity Score</strong> – 0.0–1.0 score deciding FAST vs DEEP routing.</li>
            </ul>

            <h3 style={{ marginTop: '1.5rem' }}>Design Patterns</h3>
            <ul>
              <li><strong>Pipeline</strong> – Sequential stage processing with shared context.</li>
              <li><strong>Transactional Outbox</strong> – Reliable event delivery despite downstream failures.</li>
              <li><strong>Presigned URLs</strong> – Direct client-to-S3 uploads for large files.</li>
              <li><strong>SSE Streaming</strong> – Real-time progress feedback and incremental answer delivery.</li>
              <li><strong>Multi-provider Strategy</strong> – Abstracted AI providers for flexibility and failover.</li>
              <li><strong>Iterative Refinement</strong> – DEEP mode retry loop (reasoning → verification → reflection).</li>
            </ul>

            <h3 style={{ marginTop: '1.5rem' }}>License</h3>
            <p>MIT</p>
          </section>
        )}

        <footer className="portfolio-footer">
          <p>MIT License • AI Agent Platform Documentation</p>
        </footer>
      </main>
    </div>
  );
}
