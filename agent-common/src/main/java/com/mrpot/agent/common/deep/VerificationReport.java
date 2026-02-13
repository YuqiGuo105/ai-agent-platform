package com.mrpot.agent.common.deep;

import java.util.List;

/**
 * Verification report for the deep reasoning process.
 * Contains consistency analysis and fact-checking results.
 * 
 * @param consistencyScore    overall consistency score (0.0 to 1.0)
 * @param contradictions      list of contradictions found between reasoning steps
 * @param factualityFlags     list of claim verification results
 * @param unresolvedClaims    list of claims that could not be verified
 * @param verified            whether the reasoning was verified successfully
 * @param issues              list of issues identified during verification
 * @param recommendations     recommendations for improvement
 * @param confidence          overall verification confidence (0.0 to 1.0)
 * @param timestampMs         timestamp when verification was completed
 */
public record VerificationReport(
    double consistencyScore,
    List<ContradictionFlag> contradictions,
    List<FactualityFlag> factualityFlags,
    List<String> unresolvedClaims,
    boolean verified,
    List<String> issues,
    List<String> recommendations,
    double confidence,
    long timestampMs
) {
    /**
     * A contradiction found between reasoning steps.
     */
    public record ContradictionFlag(int stepA, int stepB, String description) {}
    
    /**
     * Result of fact-checking a claim.
     */
    public record FactualityFlag(String claim, String verdict, double confidence) {}
    
    /**
     * Create a successful verification report with high consistency.
     */
    public static VerificationReport success(double confidence) {
        return new VerificationReport(
            1.0, List.of(), List.of(), List.of(),
            true, List.of(), List.of(), confidence, System.currentTimeMillis()
        );
    }
    
    /**
     * Create a verification report with issues and low consistency.
     */
    public static VerificationReport withIssues(
            double consistencyScore,
            List<ContradictionFlag> contradictions,
            List<FactualityFlag> factualityFlags,
            List<String> unresolvedClaims,
            List<String> issues,
            List<String> recommendations,
            double confidence) {
        return new VerificationReport(
            consistencyScore, contradictions, factualityFlags, unresolvedClaims,
            false, issues, recommendations, confidence, System.currentTimeMillis()
        );
    }
    
    /**
     * Create a skipped verification placeholder.
     */
    public static VerificationReport skipped() {
        return new VerificationReport(
            1.0, List.of(), List.of(), List.of(),
            false, List.of("Verification skipped"), List.of(), 0.0, System.currentTimeMillis()
        );
    }
    
    /**
     * Create a default report for error recovery.
     */
    public static VerificationReport defaultReport() {
        return new VerificationReport(
            1.0, List.of(), List.of(), List.of(),
            true, List.of(), List.of(), 0.5, System.currentTimeMillis()
        );
    }
}
