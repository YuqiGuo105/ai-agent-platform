package com.mrpot.agent.common.sse;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Server-Sent Event envelope for streaming responses")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseEnvelope(
    @Schema(description = "Event stage (start, answer_delta, tool_call_start, tool_call_result, answer_final)", example = "answer_delta")
    String stage,
    
    @Schema(description = "Human-readable message", example = "Generating answer...")
    String message,
    
    @Schema(description = "Event payload (type varies by stage)")
    Object payload,
    
    @Schema(description = "Event sequence number", example = "5")
    Long seq,
    
    @Schema(description = "Timestamp in milliseconds", example = "1707408000000")
    Long ts,
    
    @Schema(description = "Trace ID for distributed tracing", example = "abc123")
    String traceId,
    
    @Schema(description = "Session ID", example = "sess-12345")
    String sessionId
) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String stage;
    private String message;
    private Object payload;
    private Long seq;
    private Long ts;
    private String traceId;
    private String sessionId;

    private Builder() {}

    public Builder stage(String stage) {
      this.stage = stage;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder payload(Object payload) {
      this.payload = payload;
      return this;
    }

    public Builder seq(long seq) {
      this.seq = seq;
      return this;
    }

    public Builder ts(long ts) {
      this.ts = ts;
      return this;
    }

    public Builder traceId(String traceId) {
      this.traceId = traceId;
      return this;
    }

    public Builder sessionId(String sessionId) {
      this.sessionId = sessionId;
      return this;
    }

    public SseEnvelope build() {
      return new SseEnvelope(stage, message, payload, seq, ts, traceId, sessionId);
    }
  }
}
