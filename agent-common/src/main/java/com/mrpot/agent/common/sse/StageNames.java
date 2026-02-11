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
