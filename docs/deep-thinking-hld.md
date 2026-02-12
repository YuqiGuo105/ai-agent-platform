# Enhanced Deep Thinking Mode — High-Level Design

## 1) Goals and Non-Goals

### Goals
- Introduce a production-grade **DEEP** execution path that complements the current FAST pipeline.
- Preserve compatibility with the current execution framework (`PipelineRunner`, `PipelineContext`, policy stack, SSE contract).
- Add extensible MCP tool categories for reasoning, memory, planning, and verification.
- Enable finance-oriented visualization outputs (chart + table) as an MVP that scales to many diagram tools.
- Improve mode decision quality using multi-signal complexity scoring instead of only keyword matching.
- Keep implementation modular, testable, and easy to evolve.

### Non-Goals (MVP)
- Replacing FAST mode behavior.
- Implementing all finance tools now (this document proposes architecture + sprint-ready design).
- Introducing side-effect tools by default (policy remains restrictive unless explicitly allowed).

---

## 2) Current Baseline (Context)

The current system already has:
- A composable stage runner (`PipelineRunner`) with timeout, per-stage conditions, SSE emission, and graceful error handling.
- A `FastPipeline` factory with deterministic stage order.
- Shared cross-stage `PipelineContext` with mutable working memory.
- A policy + mode framework (`PolicyBuilder`, `ModeDecider`, `ExecutionPolicy`).
- MCP tool handlers based on `ToolHandler` + `ToolDefinition` + `CallToolResponse`.

This design intentionally extends these patterns rather than introducing a parallel architecture.

---

## 3) A. Deep Thinking Pipeline Architecture

## 3.1 New component: `DeepPipeline`

Create `DeepPipeline` in:
- `agent-service/src/main/java/com/mrpot/agent/service/pipeline/DeepPipeline.java`

Role:
- Same responsibility pattern as `FastPipeline`: build and return a configured `PipelineRunner`.
- Adds deeper, iterative reasoning stages while preserving existing telemetry + SSE semantics.

### 3.1.1 Stage flow (canonical)

1. `telemetry_start` (existing)
2. `history_retrieve` (existing)
3. `deep_plan` (new)
4. `deep_reasoning` (new, iterative loop-aware)
5. `deep_tool_orchestration` (new, optional/conditional)
6. `deep_reflection` (new)
7. `deep_synthesis` (new; produces final answer + UI blocks)
8. `conversation_save` (existing)
9. `telemetry_final` (existing)

### 3.1.2 Execution model

- Use `PipelineRunner` stage sequencing for top-level flow.
- Deep internals (reasoning/tool/reflection) run as **bounded micro-iterations** within stage processors:
  - max rounds from policy (`maxToolRounds`) + deep-specific cap.
  - each round appends artifacts into `PipelineContext` memory.
- Failures in tools/verification should degrade gracefully and still allow synthesis.

### 3.1.3 Integration with `PipelineFactory` and `PipelineRunner`

#### `PipelineFactory` changes
- Inject `DeepPipeline` alongside `FastPipeline`.
- In `createPipeline(context)`, route `MODE_DEEP` to `deepPipeline.build()`.
- Keep unknown modes fallback to FAST.

#### `PipelineRunner` changes (minimal + backward compatible)
- Keep current API untouched.
- Optional enhancement: support stage metadata payload convention:
  - standardized payload shape for deep-stage progress (`round`, `status`, `summary`, `latencyMs`).
- Existing event creation and error behavior remain valid.

## 3.2 `PipelineContext` working memory extension

Add typed keys and helper methods (constants + convenience accessors) for deep execution.

### 3.2.1 New working-memory keys

```text
KEY_DEEP_PLAN
KEY_REASONING_TRACE
KEY_REASONING_ROUNDS
KEY_TOOL_CALL_HISTORY
KEY_TOOL_EVIDENCE
KEY_REFLECTION_NOTES
KEY_VERIFICATION_REPORT
KEY_SYNTHESIS_BLOCKS
KEY_COMPLEXITY_SCORE
```

### 3.2.2 Suggested value models

- `DeepPlan`: objective, constraints, subtasks, success criteria.
- `ReasoningStep`: stepId, hypothesis, evidenceRefs, confidence, timestamp.
- `ToolCallRecord`: toolName, argsHash, latencyMs, success, outputRef, error.
- `ReflectionNote`: finding, risk, contradictionFlag, followupAction.
- `VerificationReport`: consistencyScore, factualityFlags, unresolvedClaims.

### 3.2.3 Memory lifecycle

- All stage outputs remain in-memory per request (`PipelineContext`).
- Selective persistence via memory tools (Redis/PostgreSQL) for cross-session recall.
- Final response should include concise reasoning summary, not raw chain-of-thought.

## 3.3 Deep stage responsibilities

- **deep_plan**:
  - decompose task; classify required evidence; produce executable plan.
- **deep_reasoning**:
  - generate hypotheses and decision tree; attach confidence.
- **deep_tool_orchestration**:
  - decide which MCP tools to call; execute; normalize outputs.
- **deep_reflection**:
  - detect contradictions, missing evidence, over-claims; request another round if needed.
- **deep_synthesis**:
  - construct final user answer + optional `UiBlock` set (`ChartBlock`, `TableBlock`).

---

## 4) B. Enhanced MCP Tools for Deep Thinking

All new tools follow current `ToolHandler` pattern:
- `name()`
- `definition()` with explicit JSON schema
- `handle(JsonNode args, ToolContext ctx)` returning `CallToolResponse`

Tool naming follows namespace conventions and read-only-by-default policy.

## 4.1 Reasoning tools

- `reasoning.compare`
  - input: entities[], dimensions[], context
  - output: structured comparison matrix + deltas
- `reasoning.analyze`
  - input: claim, evidence[], analysisType
  - output: assumptions, risks, confidence
- `reasoning.causality`
  - input: eventA, eventB, evidence[]
  - output: causal graph summary + alternative explanations

## 4.2 Memory tools

- `memory.store`
  - persists distilled facts/decisions into conversation memory
  - reuses/extends `ConversationHistoryService` key strategy with typed memory lanes:
    - `chat:history:{sessionId}` (existing)
    - `chat:memory:facts:{sessionId}`
    - `chat:memory:plans:{sessionId}`
- `memory.recall`
  - retrieves relevant memory snippets by semantic tag/time window

Implementation note:
- Keep message history and semantic memory separated to avoid prompt pollution.

## 4.3 Verification tools

- `verify.consistency`
  - checks internal logical consistency across reasoning artifacts.
- `verify.fact_check`
  - checks claims against trusted KB/docs/tools and returns claim-level verdicts.

## 4.4 Planning tools

- `planning.decompose`
  - converts high-level goal into DAG-like subtasks with dependencies.
- `planning.next_step`
  - selects best next action based on remaining uncertainty + policy constraints.

## 4.5 Tool package layout (proposed)

Under `agent-tools-service/src/main/java/com/mrpot/agent/tools/tool/`:

```text
deep/
  reasoning/ReasoningCompareTool.java
  reasoning/ReasoningAnalyzeTool.java
  reasoning/ReasoningCausalityTool.java
  memory/MemoryStoreTool.java
  memory/MemoryRecallTool.java
  verify/VerifyConsistencyTool.java
  verify/VerifyFactCheckTool.java
  planning/PlanningDecomposeTool.java
  planning/PlanningNextStepTool.java
```

Add a small shared utility layer:
- `DeepToolSchemas` (schema builders)
- `DeepToolResultNormalizer` (uniform result envelope)

This keeps handlers lean and consistent with existing tools like `KbSearchTool` and `KbGetDocumentTool`.

---

## 5) C. Financial Visualization Tools (MVP + future-ready)

## 5.1 MVP finance MCP tools (design only)

- `finance.quote.get`
  - returns `Quote`
- `finance.timeseries.get`
  - returns `TimeSeries` with `List<OhlcPoint>`
- `finance.chart.candlestick`
  - input `TimeSeries`; output `ChartBlock(kind="candlestick")`
- `finance.chart.line`
  - input `TimeSeries`; output `ChartBlock(kind="line")`
- `finance.table.summary`
  - input quote/series metrics; output `TableBlock`

## 5.2 Data mapping strategy

Use existing records in `agent-common`:
- `OhlcPoint` for OHLC candles
- `TimeSeries` for symbol interval ranges
- `Quote` for latest price snapshot

Render strategy:
- Convert typed finance objects to a chart `spec` JSON (Vega-lite/ECharts-compatible schema envelope).
- Wrap that spec into `ChartBlock`.
- For numeric breakdowns, emit `TableBlock` with normalized columns/rows.

## 5.3 Extensibility for many diagram tools

Introduce diagram abstraction layer:
- `DiagramSpecAdapter<T>` interface:
  - `supports(kind)`
  - `toChartBlock(T data, DiagramOptions options)`
- Register adapters by kind:
  - candlestick, line, area, heatmap, correlation-matrix, waterfall, etc.

Benefits:
- new diagram types do not require pipeline changes.
- finance/other domains can share diagram infrastructure.

---

## 6) D. Enhanced Mode Decision Logic

Extend `ModeDecider` from keyword heuristic to hybrid scoring.

## 6.1 Proposed decision signals

1. **Lexical complexity**
   - current keyword and length checks (retain as one feature).
2. **Structural complexity**
   - number of clauses/questions, comparative operators, constraints.
3. **Intent class**
   - planning/analysis/causal/exploration intents get higher deep weight.
4. **Evidence demand**
   - whether query likely requires tools/RAG/verification.
5. **Policy feasibility**
   - deep only if policy can support required rounds/tools.
6. **Historical interaction signal**
   - repeated follow-up clarifications indicate under-specification => deep helpful.

## 6.2 Scoring model

Compute `complexityScore` in [0,1] and compare with policy-aware threshold:

```text
if explicitDeepRequest && policy.maxToolRounds >= 3 => DEEP
else if complexityScore >= deepThreshold && policy supports deep => DEEP
else FAST
```

- Default `deepThreshold`: 0.62 (configurable).
- Persist final score in `PipelineContext.KEY_COMPLEXITY_SCORE` for telemetry and tuning.

## 6.3 Integration with `ExecutionPolicy`

No breaking changes required; optionally add non-breaking fields in future:
- `deepEnabled` (boolean)
- `deepThresholdOverride` (Double)
- `maxReasoningRounds` (int)

Until then, infer from existing fields:
- deep capability requires `maxToolRounds >= 3` and `canUseTools()` for evidence-heavy queries.

---

## 7) E. Integration Points

## 7.1 Redis (application.yaml lines 29–36)

Use Redis for:
- existing chat history retrieval/save.
- new short-term deep memory lanes (facts/plans/reflections).
- optional per-session planning cache (TTL-aligned).

Design notes:
- maintain strict TTL and max-size trim strategy.
- isolate keys by namespace to avoid collisions.

## 7.2 PostgreSQL + pgvector (lines 86–95)

Use pgvector for:
- retrieving supporting documents during deep verification/fact-check.
- storing optional embeddings for memory summaries (future phase).

Design notes:
- keep embedding dimensions aligned with configured model.
- attach provenance metadata for every retrieved evidence block.

## 7.3 RabbitMQ telemetry (lines 23–27)

Extend run events to include deep-stage metrics:
- stage latency
- reasoning rounds
- tool-call counts/success rates
- verification outcome metrics

Design notes:
- keep event schemas backward compatible (additive fields only).

## 7.4 MCP tools service (lines 147–156)

Use configured tools base URL and timeout/retry policy for deep tools.

Design notes:
- treat deep tool calls as bounded and cancelable.
- include per-tool latency and retry info in tool history artifacts.

---

## 8) F. SSE Event Flow for Deep Thinking

Current SSE supports adding new stage names safely. Add deep-specific stage names in `StageNames`.

## 8.1 New stage constants (proposed)

```text
DEEP_PLAN_START
DEEP_PLAN_DONE
DEEP_REASONING_START
DEEP_REASONING_STEP
DEEP_REASONING_DONE
DEEP_TOOL_ORCH_START
DEEP_TOOL_ORCH_RESULT
DEEP_REFLECTION
DEEP_SYNTHESIS_START
DEEP_SYNTHESIS_DONE
```

## 8.2 Event flow example

1. `start`
2. `History`
3. `deep_plan_start` / `deep_plan_done`
4. `deep_reasoning_start`
5. repeated `deep_reasoning_step`
6. `tool_call_start` / `tool_call_result` (existing names reused where possible)
7. `deep_reflection`
8. optional extra round (steps 4–7)
9. `deep_synthesis_start` / `deep_synthesis_done`
10. `answer_final`

## 8.3 Payload contracts

Each deep event payload should include:
- `runId`
- `round`
- `stage`
- `summary` (safe, user-facing)
- `metrics` (latencyMs, toolCount, confidence)

Avoid raw chain-of-thought in SSE; provide concise rationale summaries.

---

## 9) Class Design Sketch (Implementation-Oriented)

## 9.1 Core service classes

- `DeepPipeline` (builder/factory)
- `DeepPlanStage`, `DeepReasoningStage`, `DeepToolOrchestrationStage`, `DeepReflectionStage`, `DeepSynthesisStage`
- `DeepReasoningCoordinator` (round loop + stop conditions)
- `DeepArtifactStore` (typed helper over `PipelineContext` memory keys)

## 9.2 Tool service classes

- one handler per tool (`ToolHandler` pattern)
- `DeepToolInputValidator`
- `DeepToolAuditService` (records structured `ToolCallRecord`)

## 9.3 Finance visualization classes

- `FinanceChartService`
- `FinanceTableService`
- `DiagramSpecAdapter` implementations

---

## 10) Quality, Maintainability, and Extensibility Guidelines

1. **Small stage classes, single responsibility**.
2. **Typed artifacts over raw maps** (use records/DTOs, map only at boundaries).
3. **Strict schemas for all tool inputs/outputs**.
4. **Configurable thresholds/timeouts via YAML** (no hard-coded deep constants).
5. **Deterministic stage contracts** (clear input/output keys).
6. **Comprehensive tests**:
   - mode decision scoring tests
   - deep stage unit tests
   - integration tests for SSE order + fallback behavior
   - tool handler schema/validation tests
7. **Backward compatibility first**:
   - additive SSE stages
   - non-breaking policy evolution
   - FAST path unaffected
8. **Observability**:
   - traceId propagation across stages + tools
   - round-level metrics in telemetry

---

## 11) Rollout Plan (Recommended)

### Phase 1: Skeleton + routing
- add `DeepPipeline` with basic stages and no-op reasoning internals.
- enable `PipelineFactory` DEEP routing.

### Phase 2: Tooling + memory
- add planning/reasoning/verification tools.
- add memory store/recall with Redis namespaces.

### Phase 3: Finance MVP visuals
- implement quote/timeseries read tools.
- implement chart/table generation and include in synthesis output.

### Phase 4: Decision tuning
- deploy complexity scoring with telemetry feedback loop.
- tune thresholds by observed quality/latency trade-offs.

---

## 12) Risks and Mitigations

- **Risk: latency increase in DEEP mode**
  - Mitigation: strict per-stage timeout + bounded rounds + graceful synthesis.
- **Risk: overexposed reasoning details**
  - Mitigation: separate internal trace from user-facing summaries.
- **Risk: tool sprawl and inconsistent contracts**
  - Mitigation: shared schema/result utilities and naming conventions.
- **Risk: policy mismatch**
  - Mitigation: gate deep path on policy feasibility checks early.

---

## 13) Expected Outcome

After implementation, the platform will support two robust execution modes:
- **FAST** for low-latency direct responses.
- **DEEP** for multi-step analysis, tool-assisted reasoning, self-verification, and synthesis with structured visual outputs.

The architecture remains aligned with existing abstractions and is intentionally extensible for future diagram tooling and domain-specialized deep agents.
