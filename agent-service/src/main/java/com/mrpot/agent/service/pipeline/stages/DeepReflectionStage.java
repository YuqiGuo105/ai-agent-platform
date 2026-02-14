package com.mrpot.agent.service.pipeline.stages;

import com.mrpot.agent.common.deep.ReflectionNote;
import com.mrpot.agent.common.deep.VerificationReport;
import com.mrpot.agent.common.sse.SseEnvelope;
import com.mrpot.agent.common.sse.StageNames;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.Processor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Deep reflection stage - analyzes verification results and determines follow-up actions.
 * 
 * This stage:
 * - Reads verification report from context
 * - Determines if contradictions or unresolved claims require retry
 * - Produces ReflectionNote with follow-up action
 * - Sets KEY_NEEDS_ADDITIONAL_ROUND flag for coordinator
 */
@Slf4j
public class DeepReflectionStage implements Processor<Void, SseEnvelope> {
    
    private static final double CONSISTENCY_THRESHOLD = 0.7;
    private static final int UNRESOLVED_CLAIMS_THRESHOLD = 2;
    
    @Override
    public Mono<SseEnvelope> process(Void input, PipelineContext context) {
        log.debug("Starting deep reflection stage for runId={}", context.runId());
        
        return Mono.fromSupplier(() -> {
            // Get verification report from context
            VerificationReport report = context.getVerificationReport();
            
            if (report == null) {
                log.warn("No verification report found for runId={}, using default", context.runId());
                report = VerificationReport.defaultReport();
            }
            
            int currentRound = context.getCurrentRound();
            
            // Determine follow-up action based on verification results
            ReflectionNote note = determineFollowupAction(report, currentRound);
            
            // Store reflection note in context
            context.setReflectionNote(note);
            
            // Set flag for additional round
            boolean needsRetry = "retry".equals(note.followupAction());
            context.setNeedsAdditionalRound(needsRetry);
            
            log.info("Deep reflection completed for runId={}: contradictionFlag={}, followupAction={}, needsRetry={}",
                context.runId(), note.contradictionFlag(), note.followupAction(), needsRetry);
            
            // Create SSE envelope with reflection result
            return new SseEnvelope(
                StageNames.DEEP_REFLECTION,
                "Reflection: " + note.followupAction(),
                Map.of(
                    "contradictionFlag", note.contradictionFlag(),
                    "followupAction", note.followupAction(),
                    "summary", note.summary(),
                    "consistencyScore", report.consistencyScore(),
                    "unresolvedCount", report.unresolvedClaims() != null ? report.unresolvedClaims().size() : 0
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            );
        }).onErrorResume(e -> {
            log.error("Failed to complete deep reflection for runId={}: {}", 
                context.runId(), e.getMessage(), e);
            
            // Create default reflection note on error
            int currentRound = context.getCurrentRound();
            ReflectionNote defaultNote = ReflectionNote.defaultNote(currentRound);
            context.setReflectionNote(defaultNote);
            context.setNeedsAdditionalRound(false);
            
            // Return error indicator envelope
            return Mono.just(new SseEnvelope(
                StageNames.DEEP_REFLECTION,
                "Reflection fallback",
                Map.of(
                    "contradictionFlag", false,
                    "followupAction", "proceed",
                    "summary", "Proceeding with default due to reflection error",
                    "consistencyScore", 1.0,
                    "unresolvedCount", 0,
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                ),
                context.nextSeq(),
                System.currentTimeMillis(),
                context.traceId(),
                context.sessionId()
            ));
        });
    }
    
    /**
     * Determine follow-up action based on verification results.
     * 
     * Decision logic:
     * - consistencyScore < 0.7 → contradictionFlag = true, followupAction = "retry"
     * - unresolvedClaims.size() > 2 → followupAction = "retry"
     * - otherwise → followupAction = "proceed"
     */
    private ReflectionNote determineFollowupAction(VerificationReport report, int round) {
        boolean hasContradiction = report.consistencyScore() < CONSISTENCY_THRESHOLD;
        int unresolvedCount = report.unresolvedClaims() != null ? report.unresolvedClaims().size() : 0;
        boolean tooManyUnresolved = unresolvedCount > UNRESOLVED_CLAIMS_THRESHOLD;
        
        if (hasContradiction) {
            String observation = String.format(
                "Consistency score (%.2f) below threshold (%.2f). Found %d contradictions.",
                report.consistencyScore(),
                CONSISTENCY_THRESHOLD,
                report.contradictions() != null ? report.contradictions().size() : 0
            );
            String summary = "Low consistency detected, retrying with refined reasoning";
            return ReflectionNote.of(true, "retry", observation, summary, round);
        }
        
        if (tooManyUnresolved) {
            String observation = String.format(
                "Too many unresolved claims (%d > %d). Need additional evidence gathering.",
                unresolvedCount,
                UNRESOLVED_CLAIMS_THRESHOLD
            );
            String summary = "Multiple unresolved claims, retrying to gather more evidence";
            return ReflectionNote.of(false, "retry", observation, summary, round);
        }
        
        // All checks passed - proceed
        String observation = String.format(
            "Verification passed: consistency=%.2f, unresolved=%d",
            report.consistencyScore(),
            unresolvedCount
        );
        String summary = "Verification complete, proceeding to synthesis";
        return ReflectionNote.of(false, "proceed", observation, summary, round);
    }
}
