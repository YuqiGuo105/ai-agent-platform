package com.mrpot.agent.common.ui;

import java.util.List;

public record TableBlock(
    String type,
    String title,
    List<String> columns,
    List<List<Object>> rows,
    String sourceNote,
    Long sourceTs
) implements UiBlock {
  public TableBlock(String title, List<String> columns, List<List<Object>> rows, Long sourceTs) {
    this("table", title, columns, rows, null, sourceTs);
  }
}
