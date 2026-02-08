package com.mrpot.agent.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mrpot.agent.common.ui.UiAction;
import com.mrpot.agent.common.ui.UiBlock;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RagAnswerResponse(
    String traceId,
    String sessionId,
    String model,
    String answerMarkdown,
    List<UiBlock> uiBlocks,
    List<UiAction> actions
) {}
