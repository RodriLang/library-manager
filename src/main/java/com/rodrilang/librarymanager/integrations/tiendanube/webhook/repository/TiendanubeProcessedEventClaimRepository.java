package com.rodrilang.librarymanager.integrations.tiendanube.webhook.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class TiendanubeProcessedEventClaimRepository {

    private static final String ORDER_CREATED_EVENT = "order/created";
    private static final String LEGACY_ORDER_PAID_EVENT = "order/paid";

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

    public boolean tryClaimOrderStockDeduction(Long storeId, Long resourceId, Instant processedAt) {
        int inserted = jdbcTemplate.update("""
                        INSERT INTO tiendanube_processed_events (
                            store_id,
                            resource_id,
                            event,
                            processed_at
                        )
                        SELECT ?, ?, ?, ?
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM tiendanube_processed_events
                            WHERE store_id = ?
                              AND resource_id = ?
                              AND event IN (?, ?)
                        )
                        ON CONFLICT (store_id, resource_id, event) DO NOTHING
                        """,
                storeId,
                resourceId,
                ORDER_CREATED_EVENT,
                Timestamp.from(processedAt),
                storeId,
                resourceId,
                ORDER_CREATED_EVENT,
                LEGACY_ORDER_PAID_EVENT
        );

        return inserted == 1;
    }
}
