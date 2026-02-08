package com.mrpot.agent.common;

import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.ui.TableBlock;
import com.mrpot.agent.common.util.Json;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonContractTest {

  @Test
  void uiBlock_polymorphism_roundtrip() throws Exception {
    var b = new TableBlock("Prices", List.of("t", "price"), List.of(List.of(1, 2.3)), 123L);
    var json = Json.MAPPER.writeValueAsString(b);
    assertTrue(json.contains("\"type\":\"table\""));
    var obj = Json.MAPPER.readValue(json, com.mrpot.agent.common.ui.UiBlock.class);
    assertEquals("table", obj.type());
  }

  @Test
  void sse_envelope_min_fields_ok() throws Exception {
    var e = new SseEnvelope("answer_delta", "chunk", "hi", null, null, null, null);
    var json = Json.MAPPER.writeValueAsString(e);
    assertTrue(json.contains("\"stage\""));
  }
}
