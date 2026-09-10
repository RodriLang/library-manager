package com.rodrilang.librarymanager.integrations.tiendanube.webhook.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.webhook.dto.TiendanubeWebhookPendingEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.enums.TiendanubeWebhookEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TiendanubeWebhookEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public Long insert(Long tiendanubeStoreId, Long storeId, String event, Long resourceId, String payload,
                       TiendanubeWebhookEventStatus status, int maxAttempts, Instant now,
                       String errorType, String errorMessage) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO tiendanube_webhook_events (
                            tiendanube_store_id,
                            store_id,
                            event,
                            resource_id,
                            payload,
                            status,
                            attempt_count,
                            max_attempts,
                            next_attempt_at,
                            completed_at,
                            last_error_type,
                            last_error_message,
                            received_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, 0, ?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                tiendanubeStoreId,
                storeId,
                event,
                resourceId,
                payload,
                status.name(),
                maxAttempts,
                timestamp(now),
                isTerminal(status) ? timestamp(now) : null,
                errorType,
                errorMessage,
                timestamp(now),
                timestamp(now)
        );
    }

    public int recoverExpiredLeases(Instant now) {
        return jdbcTemplate.update("""
                        UPDATE tiendanube_webhook_events
                        SET status = CASE
                                WHEN attempt_count >= max_attempts THEN 'FAILED'
                                ELSE 'RETRY_WAIT'
                            END,
                            next_attempt_at = ?,
                            completed_at = CASE
                                WHEN attempt_count >= max_attempts THEN ?
                                ELSE completed_at
                            END,
                            processing_token = NULL,
                            processing_started_at = NULL,
                            lease_expires_at = NULL,
                            last_error_type = 'LEASE_EXPIRED',
                            last_error_message = 'Se recuperó un webhook cuyo lease de procesamiento expiró',
                            updated_at = ?
                        WHERE status = 'PROCESSING'
                          AND lease_expires_at IS NOT NULL
                          AND lease_expires_at <= ?
                        """,
                timestamp(now),
                timestamp(now),
                timestamp(now),
                timestamp(now)
        );
    }

    public List<TiendanubeWebhookPendingEvent> lockDueEvents(int batchSize, Instant now) {
        return jdbcTemplate.query("""
                        SELECT
                            id,
                            tiendanube_store_id,
                            store_id,
                            event,
                            resource_id,
                            payload::text AS payload,
                            attempt_count,
                            max_attempts
                        FROM tiendanube_webhook_events
                        WHERE status IN ('PENDING', 'RETRY_WAIT')
                          AND next_attempt_at <= ?
                        ORDER BY next_attempt_at, received_at, id
                        FOR UPDATE SKIP LOCKED
                        LIMIT ?
                        """,
                (rs, rowNum) -> new TiendanubeWebhookPendingEvent(
                        rs.getLong("id"),
                        rs.getObject("tiendanube_store_id", Long.class),
                        rs.getLong("store_id"),
                        rs.getString("event"),
                        rs.getObject("resource_id", Long.class),
                        rs.getString("payload"),
                        rs.getInt("attempt_count"),
                        rs.getInt("max_attempts")
                ),
                timestamp(now),
                batchSize
        );
    }

    public Optional<Instant> findNextWakeAt() {
        return jdbcTemplate.query("""
                SELECT MIN(next_wake_at) AS next_wake_at
                FROM (
                    SELECT next_attempt_at AS next_wake_at
                    FROM tiendanube_webhook_events
                    WHERE status IN ('PENDING', 'RETRY_WAIT')
                
                    UNION ALL
                
                    SELECT lease_expires_at AS next_wake_at
                    FROM tiendanube_webhook_events
                    WHERE status = 'PROCESSING'
                      AND lease_expires_at IS NOT NULL
                ) wakeups
                """, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            Timestamp value = rs.getTimestamp("next_wake_at");
            return value == null ? Optional.empty() : Optional.of(value.toInstant());
        });
    }

    public int markProcessing(Long eventId, UUID token, Instant startedAt, Instant leaseExpiresAt) {
        return jdbcTemplate.update("""
                        UPDATE tiendanube_webhook_events
                        SET status = 'PROCESSING',
                            attempt_count = attempt_count + 1,
                            processing_token = ?,
                            processing_started_at = ?,
                            lease_expires_at = ?,
                            last_error_type = NULL,
                            last_error_message = NULL,
                            updated_at = ?
                        WHERE id = ?
                          AND status IN ('PENDING', 'RETRY_WAIT')
                        """,
                token,
                timestamp(startedAt),
                timestamp(leaseExpiresAt),
                timestamp(startedAt),
                eventId
        );
    }

    public int markCompleted(Long eventId, UUID token, Instant completedAt) {
        return jdbcTemplate.update("""
                        UPDATE tiendanube_webhook_events
                        SET status = 'COMPLETED',
                            completed_at = ?,
                            processing_token = NULL,
                            processing_started_at = NULL,
                            lease_expires_at = NULL,
                            last_error_type = NULL,
                            last_error_message = NULL,
                            updated_at = ?
                        WHERE id = ?
                          AND status = 'PROCESSING'
                          AND processing_token = ?
                        """,
                timestamp(completedAt),
                timestamp(completedAt),
                eventId,
                token
        );
    }

    public int markRetry(Long eventId, UUID token, Instant nextAttemptAt, Instant now,
                         String errorType, String errorMessage) {
        return jdbcTemplate.update("""
                        UPDATE tiendanube_webhook_events
                        SET status = 'RETRY_WAIT',
                            next_attempt_at = ?,
                            processing_token = NULL,
                            processing_started_at = NULL,
                            lease_expires_at = NULL,
                            last_error_type = ?,
                            last_error_message = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND status = 'PROCESSING'
                          AND processing_token = ?
                        """,
                timestamp(nextAttemptAt),
                errorType,
                errorMessage,
                timestamp(now),
                eventId,
                token
        );
    }

    public int markFailed(Long eventId, UUID token, Instant now, String errorType, String errorMessage) {
        return jdbcTemplate.update("""
                        UPDATE tiendanube_webhook_events
                        SET status = 'FAILED',
                            completed_at = ?,
                            processing_token = NULL,
                            processing_started_at = NULL,
                            lease_expires_at = NULL,
                            last_error_type = ?,
                            last_error_message = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND status = 'PROCESSING'
                          AND processing_token = ?
                        """,
                timestamp(now),
                errorType,
                errorMessage,
                timestamp(now),
                eventId,
                token
        );
    }

    private boolean isTerminal(TiendanubeWebhookEventStatus status) {
        return status == TiendanubeWebhookEventStatus.COMPLETED
                || status == TiendanubeWebhookEventStatus.FAILED
                || status == TiendanubeWebhookEventStatus.IGNORED;
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
