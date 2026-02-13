package com.mrpot.agent.common.deep;

import java.util.List;

/**
 * Verification report for the deep reasoning process.
 * Placeholder structure for Sprint 4 expansion.
 * 
 * @param verified        whether the reasoning was verified successfully
 * @param issues          list of issues identified during verification
 * @param recommendations recommendations for improvement
 * @param confidence      overall verification confidence (0.0 to 1.0)
 * @param timestampMs     timestamp when verification was completed
 */
public record VerificationReport(
    boolean verified,
    List<String> issues,
    List<String> recommendations,
    double confidence,
    long timestampMs
) {
    /**
     * Create a successful verification report.
     */
    public static VerificationReport success(double confidence) {
        return new VerificationReport(true, List.of(), List.of(), confidence, System.currentTimeMillis());
    }
    
    /**
     * Create a verification report with issues.
     */
    public static VerificationReport withIssues(List<String> issues, List<String> recommendations, double confidence) {
        return new VerificationReport(false, issues, recommendations, confidence, System.currentTimeMillis());
    }
    
    /**
     * Create a skipped verification placeholder.
     */
    public static VerificationReport skipped() {
        return new VerificationReport(false, List.of("Verification skipped"), List.of(), 0.0, System.currentTimeMillis());
    }
}
