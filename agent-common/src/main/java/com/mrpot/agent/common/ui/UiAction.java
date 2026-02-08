package com.mrpot.agent.common.ui;

import java.util.Map;

public record UiAction(
    String id,
    String label,
    String actionType,
    Map<String, Object> params
) {}
