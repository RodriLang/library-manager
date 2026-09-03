package com.rodrilang.librarymanager.integrations.tiendanube.ratelimit.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TiendanubeApiRateLimitRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<Instant> findBlockedUntil(Long tiendanubeStoreId, Long remoteStoreId) {
        return jdbcTemplate.query(
                "SELECT blocked_until FROM tiendanube_api_rate_limits WHERE tiendanube_store_id = ? AND remote_store_id = ?",
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    Timestamp value = rs.getTimestamp("blocked_until");
                    return value == null ? Optional.empty() : Optional.of(value.toInstant());
                },
                tiendanubeStoreId,
                remoteStoreId
        );
    }

    public void upsert(Long tiendanubeStoreId, Long remoteStoreId, Integer limitCapacity, Integer remaining,
                       Long resetAfterMs, Instant blockedUntil) {
        jdbcTemplate.update("""
                INSERT INTO tiendanube_api_rate_limits (
                    tiendanube_store_id,
                    remote_store_id,
                    limit_capacity,
                    remaining,
                    reset_after_ms,
                    blocked_until,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (tiendanube_store_id) DO UPDATE SET
                    remote_store_id = EXCLUDED.remote_store_id,
                    limit_capacity = EXCLUDED.limit_capacity,
                    remaining = EXCLUDED.remaining,
                    reset_after_ms = EXCLUDED.reset_after_ms,
                    blocked_until = EXCLUDED.blocked_until,
                    updated_at = NOW()
                """,
                tiendanubeStoreId,
                remoteStoreId,
                limitCapacity,
                remaining,
                resetAfterMs,
                blockedUntil == null ? null : Timestamp.from(blockedUntil)
        );
    }
}
