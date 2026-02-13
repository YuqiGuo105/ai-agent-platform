package com.mrpot.agent.listener;

import com.mrpot.agent.common.replay.ReplayCommand;
import com.mrpot.agent.config.CommandAmqpConfig;
import com.mrpot.agent.service.pipeline.PipelineContext;
import com.mrpot.agent.service.pipeline.PipelineFactory;
import com.mrpot.agent.service.pipeline.PipelineRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayCommandListener {

    private final PipelineFactory pipelineFactory;

    @RabbitListener(queues = CommandAmqpConfig.AGENT_COMMAND_QUEUE)
    public void handleReplayCommand(ReplayCommand command) {
        log.info("Received replay command: parentRunId={}, newRunId={}, mode={}",
            command.getParentRunId(), command.getNewRunId(), command.getMode());

        try {
            PipelineContext context = pipelineFactory.createReplayContext(command);
            PipelineRunner pipeline = pipelineFactory.createPipeline(context);

            pipeline.run(context)
                .doOnComplete(() -> log.info("Replay pipeline completed: runId={}", command.getNewRunId()))
                .doOnError(e -> log.error("Replay pipeline failed: runId={}, error={}",
                    command.getNewRunId(), e.getMessage(), e))
                .subscribe();
        } catch (Exception e) {
            log.error("Failed to start replay pipeline: parentRunId={}, newRunId={}, error={}",
                command.getParentRunId(), command.getNewRunId(), e.getMessage(), e);
        }
    }
}
