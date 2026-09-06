package com.rodrilang.librarymanager.integrations.tiendanube.webhook.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class TiendanubeProcessedEventClaimRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean tryClaim(Long storeId, Long resourceId, String event, Instant processedAt) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO tiendanube_processed_events (
                    store_id,
                    resource_id,
                    event,
                    processed_at
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT (store_id, resource_id, event) DO NOTHING
                """,
                storeId,
                resourceId,
                event,
                Timestamp.from(processedAt)
        );

        return inserted == 1;
    }
}
