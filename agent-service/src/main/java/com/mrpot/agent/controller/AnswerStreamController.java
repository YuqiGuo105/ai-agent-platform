package com.mrpot.agent.controller;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.PipelineFactory;
import com.mrpot.agent.service.pipeline.PipelineRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Answer Stream Controller - provides RAG-augmented AI answer generation API.
 * 
 * Uses modular pipeline architecture to execute the following stages:
 * 1. Telemetry start event
 * 2. Conversation history retrieval
 * 3. File extraction (conditional)
 * 4. RAG retrieval (conditional)
 * 5. LLM streaming response
 * 6. Conversation save
 * 7. Telemetry final event
 */
@RestController
@RequestMapping("/answer")
@Tag(name = "Answer Stream", description = "RAG-powered answer generation with SSE streaming")
public class AnswerStreamController {
  
  private final PipelineFactory pipelineFactory;

  public AnswerStreamController(PipelineFactory pipelineFactory) {
    this.pipelineFactory = pipelineFactory;
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "Stream RAG answer with SSE",
      description = "Generate AI-powered answers using RAG (Retrieval-Augmented Generation) with real-time Server-Sent Events streaming. " +
                    "Supports tool calling via MCP protocol and emits structured events (start, tool_call_start, tool_call_result, answer_delta, answer_final)."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "SSE stream of answer generation events",
          content = @Content(
              mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
              schema = @Schema(implementation = SseEnvelope.class),
              examples = @ExampleObject(
                  name = "SSE Event Stream",
                  value = "{\"stage\":\"start\",\"message\":\"Starting\",\"seq\":1,\"ts\":1707408000000,\"traceId\":\"abc123\",\"sessionId\":\"sess1\"}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid request parameters"
      ),
      @ApiResponse(
          responseCode = "500",
          description = "Internal server error during answer generation"
      )
  })
  public Flux<SseEnvelope> stream(
      @Parameter(
          description = "RAG answer request containing question, session context, and optional tool profile",
          required = true,
          schema = @Schema(implementation = RagAnswerRequest.class)
      )
      @RequestBody RagAnswerRequest request
  ) {
    // Generate trace ID
    String traceId = UUID.randomUUID().toString();
    
    // Create pipeline context and pipeline runner
    PipelineContext context = pipelineFactory.createContext(request, traceId);
    PipelineRunner pipeline = pipelineFactory.createPipeline(context);
    
    // Execute pipeline and return SSE event stream
    return pipeline.run(context);
  }
}
