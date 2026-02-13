package com.mrpot.agent.service.pipeline.artifacts;

/**
 * Verification report for the final answer.
 * Placeholder for Sprint 4 verification integration.
 * 
 * @param verified    whether the answer was verified
 * @param summary     summary of the verification result
 * @param timestampMs timestamp when verification completed
 */
public record VerificationReport(
    boolean verified,
    String summary,
    long timestampMs
) {
    /**
     * Create a skipped verification report.
     */
    public static VerificationReport skipped() {
        return new VerificationReport(false, "Verification skipped", System.currentTimeMillis());
    }
    
    /**
     * Create a passed verification report.
     */
    public static VerificationReport passed(String summary) {
        return new VerificationReport(true, summary, System.currentTimeMillis());
    }
}
