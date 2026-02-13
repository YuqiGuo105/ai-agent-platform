package com.mrpot.agent.service.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for DEEP mode execution.
 */
@Component
@ConfigurationProperties(prefix = "deep")
public class DeepModeConfig {
    
    /**
     * Maximum reasoning rounds cap (safety limit).
     */
    private int maxRoundsCap = 5;
    
    /**
     * Total timeout for reasoning phase in seconds.
     */
    private int reasoningTimeoutSeconds = 120;
    
    /**
     * Confidence threshold to stop reasoning early.
     */
    private double confidenceThreshold = 0.85;
    
    /**
     * Timeout for plan generation in seconds.
     */
    private int planTimeoutSeconds = 30;
    
    /**
     * Complexity threshold for automatic DEEP mode (0.0-1.0).
     */
    private double complexityThreshold = 0.6;
    
    /**
     * Maximum tool call rounds cap (safety limit).
     */
    private int maxToolRoundsCap = 10;
    
    /**
     * Timeout for individual tool calls in seconds.
     */
    private int toolTimeoutSeconds = 30;
    
    // Getters and Setters
    
    public int getMaxRoundsCap() {
        return maxRoundsCap;
    }
    
    public void setMaxRoundsCap(int maxRoundsCap) {
        this.maxRoundsCap = maxRoundsCap;
    }
    
    public int getReasoningTimeoutSeconds() {
        return reasoningTimeoutSeconds;
    }
    
    public void setReasoningTimeoutSeconds(int reasoningTimeoutSeconds) {
        this.reasoningTimeoutSeconds = reasoningTimeoutSeconds;
    }
    
    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }
    
    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }
    
    public int getPlanTimeoutSeconds() {
        return planTimeoutSeconds;
    }
    
    public void setPlanTimeoutSeconds(int planTimeoutSeconds) {
        this.planTimeoutSeconds = planTimeoutSeconds;
    }
    
    public double getComplexityThreshold() {
        return complexityThreshold;
    }
    
    public void setComplexityThreshold(double complexityThreshold) {
        this.complexityThreshold = complexityThreshold;
    }
    
    public int getMaxToolRoundsCap() {
        return maxToolRoundsCap;
    }
    
    public void setMaxToolRoundsCap(int maxToolRoundsCap) {
        this.maxToolRoundsCap = maxToolRoundsCap;
    }
    
    public int getToolTimeoutSeconds() {
        return toolTimeoutSeconds;
    }
    
    public void setToolTimeoutSeconds(int toolTimeoutSeconds) {
        this.toolTimeoutSeconds = toolTimeoutSeconds;
    }
}
