package com.mrpot.agent.tool;

public class ToolMetadata {

    private String toolName;
    private boolean replayable;
    private boolean hasSideEffects;
    private boolean requiresPermission;

    public ToolMetadata() {}

    public ToolMetadata(String toolName, boolean replayable, boolean hasSideEffects, boolean requiresPermission) {
        this.toolName = toolName;
        this.replayable = replayable;
        this.hasSideEffects = hasSideEffects;
        this.requiresPermission = requiresPermission;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public boolean isReplayable() {
        return replayable;
    }

    public void setReplayable(boolean replayable) {
        this.replayable = replayable;
    }

    public boolean isHasSideEffects() {
        return hasSideEffects;
    }

    public void setHasSideEffects(boolean hasSideEffects) {
        this.hasSideEffects = hasSideEffects;
    }

    public boolean isRequiresPermission() {
        return requiresPermission;
    }

    public void setRequiresPermission(boolean requiresPermission) {
        this.requiresPermission = requiresPermission;
    }
}
