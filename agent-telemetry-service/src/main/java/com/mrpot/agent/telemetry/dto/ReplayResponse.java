package com.mrpot.agent.telemetry.dto;

public class ReplayResponse {

    private String newRunId;

    public ReplayResponse() {}

    public ReplayResponse(String newRunId) {
        this.newRunId = newRunId;
    }

    public String getNewRunId() {
        return newRunId;
    }

    public void setNewRunId(String newRunId) {
        this.newRunId = newRunId;
    }
}
