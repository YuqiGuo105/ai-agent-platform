package com.mrpot.agent.telemetry.dto;

import java.util.List;

public class ReplayCommand {

    private String parentRunId;
    private String newRunId;
    private ReplayMode mode;
    private List<String> allowedTools;
    private String question;
    private String sessionId;
    private String userId;
    private String model;

    public ReplayCommand() {}

    public String getParentRunId() {
        return parentRunId;
    }

    public void setParentRunId(String parentRunId) {
        this.parentRunId = parentRunId;
    }

    public String getNewRunId() {
        return newRunId;
    }

    public void setNewRunId(String newRunId) {
        this.newRunId = newRunId;
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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
