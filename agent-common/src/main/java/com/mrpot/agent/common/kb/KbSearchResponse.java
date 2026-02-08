package com.mrpot.agent.common.kb;

import java.util.List;

public record KbSearchResponse(
    List<KbDocument> docs,
    List<KbHit> hits,
    String contextText,
    Long sourceTs
) {}
