package com.mrpot.agent.controller;

import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.service.AnswerStreamOrchestrator;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/answer")
public class AnswerStreamController {
  private final AnswerStreamOrchestrator orchestrator;

  public AnswerStreamController(AnswerStreamOrchestrator orchestrator) {
    this.orchestrator = orchestrator;
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<SseEnvelope> stream(@RequestBody RagAnswerRequest request) {
    return orchestrator.stream(request, UUID.randomUUID().toString());
  }
}
