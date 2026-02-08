package com.mrpot.agent.common.kb;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KbDocument(
    String id,
    String docType,
    String title,
    String content,
    Map<String, Object> metadata
) {}
