package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeClaimedJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeJobProcessor {

    private final TiendanubeJobAttemptService attemptService;
    private final TiendanubeJobExecutor executor;
    private final TiendanubeJobFailureFactory failureFactory;
    private final TiendanubeJobCompletionService completionService;

    public void process(TiendanubeClaimedJob claimedJob) {
        attemptService.startAttempt(claimedJob.jobId(), claimedJob.processingToken())
                .ifPresent(this::execute);
    }

    private void execute(TiendanubeJobExecutionContext context) {
        try {
            executor.execute(context);
            completionService.complete(context);

            log.info(
                    "Tiendanube job completed. jobId={} type={} inventoryId={} attempt={}",
                    context.jobId(), context.type(), context.inventoryId(), context.attemptNumber()
            );
        } catch (RuntimeException exception) {
            TiendanubeJobFailure failure = failureFactory.from(exception);
            completionService.fail(context, failure);

            log.warn(
                    "Tiendanube job failed. jobId={} type={} inventoryId={} attempt={} disposition={} errorType={}",
                    context.jobId(), context.type(), context.inventoryId(), context.attemptNumber(),
                    failure.disposition(), failure.errorType(), exception
            );
        }
    }
}
