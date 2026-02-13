package com.mrpot.agent.telemetry.dto;

import java.util.List;

public class ReplayRequest {

    private ReplayMode mode;
    private List<String> allowedTools;

    public ReplayRequest() {}

    public ReplayRequest(ReplayMode mode, List<String> allowedTools) {
        this.mode = mode;
        this.allowedTools = allowedTools;
    }

    public ReplayMode getMode() {
        return mode;
    }

    public void setMode(ReplayMode mode) {
        this.mode = mode;
    }

    public List<String> getAllowedTools() {
        return allowedTools;
    }

    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools;
    }
}
