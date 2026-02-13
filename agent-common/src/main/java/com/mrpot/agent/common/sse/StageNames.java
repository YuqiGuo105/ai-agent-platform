package com.mrpot.agent.common.sse;

public final class StageNames {
  private StageNames() {}

  // Must be compatible with existing frontend - DO NOT MODIFY OR REMOVE
  public static final String ANSWER_DELTA = "answer_delta";
  public static final String ANSWER_FINAL = "answer_final";

  // New stages (safe to add - unknown stages are ignored by old frontend)
  public static final String START = "start";
  public static final String PLAN = "plan";
  public static final String REDIS = "History";

  // Deep mode stages (safe to add - ignored by old frontend)
  public static final String DEEP_PLAN = "deep_plan";
  public static final String DEEP_PLAN_START = "deep_plan_start";
  public static final String DEEP_PLAN_DONE = "deep_plan_done";
  public static final String DEEP_REASONING = "deep_reasoning";
  public static final String DEEP_REASONING_START = "deep_reasoning_start";
  public static final String DEEP_REASONING_STEP = "deep_reasoning_step";
  public static final String DEEP_REASONING_DONE = "deep_reasoning_done";
  public static final String DEEP_SYNTHESIS = "deep_synthesis";
  public static final String FILE_EXTRACT_START = "file_extract_start";
  public static final String FILE_EXTRACT = "file_extract";
  public static final String FILE_EXTRACT_DONE = "file_extract_done";
  public static final String RAG = "Searching";
  public static final String TOOL_CALL = "tool_call";
  public static final String TOOL_CALL_START = "tool_call_start";
  public static final String TOOL_CALL_RESULT = "tool_call_result";
  public static final String TOOL_CALL_ERROR = "tool_call_error";
  public static final String VERIFY = "verify";
  public static final String ERROR = "error";
}
