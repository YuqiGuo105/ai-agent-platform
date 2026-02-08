package com.mrpot.agent.common.ui;

import com.fasterxml.jackson.databind.JsonNode;

public record ChartBlock(
    String type,
    String title,
    String kind,
    JsonNode spec,
    Long sourceTs
) implements UiBlock {
  public ChartBlock(String title, String kind, JsonNode spec, Long sourceTs) {
    this("chart", title, kind, spec, sourceTs);
  }
}
