package com.mrpot.agent.common.api;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Request payload for generating an answer with RAG context.
 *
 * @param question     user question
 * @param sessionId    chat session id for memory separation
 * @param model        optional model name hint (e.g. "deepseek", "openai", "gemini")
 * @param fileUrls     optional external file URLs for attachment (max 3)
 * @param scopeMode    scope mode for privacy/scope checks (defaults to AUTO)
 * @param toolProfile  optional tool profile (e.g. "BASIC", "DEFAULT", "FULL")
 * @param mode         execution mode for reasoning (e.g. "FAST", "DEEPTHINKING"); defaults to "FAST"
 * @param options      optional RAG options including topK (defaults to 2) and minScore (defaults to 0.10)
 * @param ext          extensible map for future fields without breaking deserialization
 */
public record RagAnswerRequest(
    String question,
    String sessionId,
    String model,
    @Size(max = 3) List<String> fileUrls,
    ScopeMode scopeMode,
    ToolProfile toolProfile,
    String mode,
    RagOptions options,
    Map<String, Object> ext
) {
  private static final String DEFAULT_MODEL = "deepseek";
  private static final String DEFAULT_MODE = "FAST";
  private static final int DEFAULT_TOP_K = 2;
  private static final double MIN_SCORE_DEFAULT = 0.10;
  private static final Set<String> MODELS = Set.of("deepseek", "gemini", "openai");

  public String resolveModel() {
    return (model == null || model.isBlank() || !MODELS.contains(model)) ? DEFAULT_MODEL : model;
  }

  public String resolveMode() {
    return (mode == null || mode.isBlank()) ? DEFAULT_MODE : mode;
  }

  public ScopeMode resolveScopeMode() {
    return scopeMode == null ? ScopeMode.AUTO : scopeMode;
  }

  public int resolveTopK() {
    if (options == null || options.topK() == null) return DEFAULT_TOP_K;
    return options.topK();
  }

  public double resolveMinScore() {
    if (options != null && options.minScore() != null) return options.minScore();
    return MIN_SCORE_DEFAULT;
  }

  public List<String> resolveFileUrls(int maxFiles) {
    if (this.fileUrls == null) return List.of();
    int limit = Math.min(Math.max(0, maxFiles), 3);
    return this.fileUrls.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .distinct()
        .limit(limit)
        .toList();
  }

  public ResolvedSession resolveSession() {
    boolean temporary = sessionId == null || sessionId.isBlank();
    String resolvedId = temporary ? "temp-" + UUID.randomUUID() : sessionId;
    return new ResolvedSession(resolvedId, temporary);
  }

  public record ResolvedSession(String id, boolean temporary) {
  }
}
