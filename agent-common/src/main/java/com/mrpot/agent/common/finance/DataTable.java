package com.mrpot.agent.common.finance;

import java.util.List;

public record DataTable(
    List<String> columns,
    List<List<Object>> rows,
    Long sourceTs,
    String provider
) {}
