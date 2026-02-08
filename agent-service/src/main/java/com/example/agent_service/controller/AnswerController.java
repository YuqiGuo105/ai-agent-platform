package com.example.agent_service.controller;

import com.example.agent_service.service.AnswerStreamService;
import com.mrpot.agent.common.api.RagAnswerRequest;
import com.mrpot.agent.common.sse.SseEnvelope;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/answer")
public class AnswerController {

  private final AnswerStreamService answerStreamService;

  public AnswerController(AnswerStreamService answerStreamService) {
    this.answerStreamService = answerStreamService;
  }

  @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<SseEnvelope> stream(@RequestBody RagAnswerRequest request) {
    return answerStreamService.stream(request);
  }
}
