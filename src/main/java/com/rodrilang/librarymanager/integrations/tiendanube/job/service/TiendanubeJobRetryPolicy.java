package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobFailure;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TiendanubeJobRetryPolicy {

    private static final Duration[] DELAYS = {
            Duration.ofSeconds(15),
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(20),
            Duration.ofHours(1),
            Duration.ofHours(6)
    };

    public boolean shouldRetry(TiendanubeJobFailure failure, int attemptNumber, int maxAttempts) {
        return failure.disposition() == TiendanubeJobFailureDisposition.RETRY && attemptNumber < maxAttempts;
    }

    public Duration nextDelay(TiendanubeJobFailure failure, int attemptNumber) {
        if (failure.retryAfter() != null && !failure.retryAfter().isNegative() && !failure.retryAfter().isZero()) {
            return failure.retryAfter().plusMillis(ThreadLocalRandom.current().nextLong(50, 251));
        }

        int index = Math.min(Math.max(attemptNumber - 1, 0), DELAYS.length - 1);
        Duration base = DELAYS[index];
        long jitterBound = Math.max(1, base.toMillis() / 5);
        long jitter = ThreadLocalRandom.current().nextLong(-jitterBound, jitterBound + 1);
        return base.plusMillis(jitter);
    }

    public Duration nextDelay(int attemptNumber) {
        return nextDelay(new TiendanubeJobFailure(
                "LEGACY_RETRY", "Legacy retry", null, TiendanubeJobFailureDisposition.RETRY, null
        ), attemptNumber);
    }
}
