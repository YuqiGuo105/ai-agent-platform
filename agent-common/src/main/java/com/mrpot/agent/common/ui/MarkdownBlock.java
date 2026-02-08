package com.mrpot.agent.common.ui;

public record MarkdownBlock(String type, String markdown) implements UiBlock {
  public MarkdownBlock(String markdown) {
    this("markdown", markdown);
  }
}
