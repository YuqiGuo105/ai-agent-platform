package com.mrpot.agent.common.finance;

public record OhlcPoint(
    long t,
    double open,
    double high,
    double low,
    double close,
    Double volume
) {}
