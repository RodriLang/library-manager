package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobFailure;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiendanubeJobRetryPolicyTest {

    private final TiendanubeJobRetryPolicy policy = new TiendanubeJobRetryPolicy();

    @Test
    void shouldRetryRetryableFailureWhileAttemptsRemain() {
        TiendanubeJobFailure failure = new TiendanubeJobFailure(
                "TIMEOUT", "Timeout", null, TiendanubeJobFailureDisposition.RETRY, null
        );

        assertTrue(policy.shouldRetry(failure, 1, 7));
        assertFalse(policy.shouldRetry(failure, 7, 7));
    }

    @Test
    void shouldNotRetryFailedOrBlockedFailure() {
        TiendanubeJobFailure failed = new TiendanubeJobFailure(
                "VALIDATION", "Invalid", 422, TiendanubeJobFailureDisposition.FAIL, null
        );
        TiendanubeJobFailure blocked = new TiendanubeJobFailure(
                "UNAUTHORIZED", "Unauthorized", 401, TiendanubeJobFailureDisposition.BLOCK, null
        );

        assertFalse(policy.shouldRetry(failed, 1, 7));
        assertFalse(policy.shouldRetry(blocked, 1, 7));
    }

    @Test
    void nextDelayAddsAtMostTwentyPercentJitter() {
        Duration delay = policy.nextDelay(1);

        assertTrue(delay.compareTo(Duration.ofSeconds(12)) >= 0);
        assertTrue(delay.compareTo(Duration.ofSeconds(18)) <= 0);
    }

    @Test
    void explicitRetryAfterTakesPrecedenceOverBackoff() {
        TiendanubeJobFailure failure = new TiendanubeJobFailure(
                "RATE_LIMIT:429", "Too many requests", 429,
                TiendanubeJobFailureDisposition.RETRY, Duration.ofMillis(600)
        );

        Duration delay = policy.nextDelay(failure, 3);

        assertTrue(delay.compareTo(Duration.ofMillis(650)) >= 0);
        assertTrue(delay.compareTo(Duration.ofMillis(850)) <= 0);
    }
}
