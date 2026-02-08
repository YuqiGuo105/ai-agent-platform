package com.mrpot.agent.common.ui;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MarkdownBlock.class, name = "markdown"),
    @JsonSubTypes.Type(value = TableBlock.class, name = "table"),
    @JsonSubTypes.Type(value = ChartBlock.class, name = "chart")
})
public sealed interface UiBlock permits MarkdownBlock, TableBlock, ChartBlock {
  String type();
}
