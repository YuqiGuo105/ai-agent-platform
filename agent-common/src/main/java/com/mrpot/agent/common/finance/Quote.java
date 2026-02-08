package com.mrpot.agent.common.finance;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Quote(
    String symbol,
    Double price,
    Double change,
    Double changePct,
    Long sourceTs,
    String provider
) {}
