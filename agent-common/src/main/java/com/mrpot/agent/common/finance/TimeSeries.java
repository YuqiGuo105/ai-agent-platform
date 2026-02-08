package com.mrpot.agent.common.finance;

import java.util.List;

public record TimeSeries(
    String symbol,
    String interval,
    String range,
    List<OhlcPoint> points,
    Long sourceTs,
    String provider
) {}
