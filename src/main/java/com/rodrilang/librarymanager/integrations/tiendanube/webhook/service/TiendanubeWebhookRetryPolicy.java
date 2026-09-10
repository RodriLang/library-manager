package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TiendanubeWebhookRetryPolicy {

    @Value("${tiendanube.webhook-inbox.retry-base-seconds:30}")
    private long baseSeconds;

    @Value("${tiendanube.webhook-inbox.retry-max-seconds:1800}")
    private long maxSeconds;

    public boolean canRetry(int attemptCount, int maxAttempts) {
        return attemptCount < maxAttempts;
    }

    public Instant nextAttemptAt(int attemptCount, Instant now) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 20));
        long multiplier = 1L << exponent;
        long delay = Math.min(maxSeconds, safeMultiply(baseSeconds, multiplier));
        return now.plusSeconds(delay);
    }

    private long safeMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 1;
        }

        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }

        return left * right;
    }
}
