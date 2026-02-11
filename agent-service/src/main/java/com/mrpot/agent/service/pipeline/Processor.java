package com.mrpot.agent.service.pipeline;

import reactor.core.publisher.Mono;

/**
 * Functional interface for pipeline processors.
 * Each stage of the pipeline implements this interface to process input and produce output.
 *
 * @param <I> input type
 * @param <O> output type
 */
@FunctionalInterface
public interface Processor<I, O> {
    
    /**
     * Process the input and produce output within the given pipeline context.
     *
     * @param input   the input to process (may be null for stages that don't need input)
     * @param context the pipeline context containing request info, working memory, etc.
     * @return Mono containing the output
     */
    Mono<O> process(I input, PipelineContext context);
}
