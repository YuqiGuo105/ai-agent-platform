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
}
