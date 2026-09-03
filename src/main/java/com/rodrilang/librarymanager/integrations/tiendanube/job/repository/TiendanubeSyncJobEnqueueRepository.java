package com.rodrilang.librarymanager.integrations.tiendanube.job.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobEnqueueCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class TiendanubeSyncJobEnqueueRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Long enqueue(TiendanubeJobEnqueueCommand command, int maxAttempts, Instant now) {
        String sql = """
                INSERT INTO tiendanube_sync_jobs (
                    bookstore_id,
                    tiendanube_store_id,
                    store_id,
                    inventory_id,
                    type,
                    status,
                    source,
                    attempt_count,
                    max_attempts,
                    next_attempt_at,
                    created_at,
                    updated_at
                ) VALUES (
                    :bookstoreId,
                    :tiendanubeStoreId,
                    :storeId,
                    :inventoryId,
                    :type,
                    'PENDING',
                    :source,
                    0,
                    :maxAttempts,
                    :now,
                    :now,
                    :now
                )
                ON CONFLICT (inventory_id, type)
                    WHERE status IN ('PENDING', 'RETRY_WAIT')
                DO UPDATE SET
                    bookstore_id = EXCLUDED.bookstore_id,
                    tiendanube_store_id = EXCLUDED.tiendanube_store_id,
                    store_id = EXCLUDED.store_id,
                    source = EXCLUDED.source,
                    attempt_count = CASE
                        WHEN tiendanube_sync_jobs.tiendanube_store_id <> EXCLUDED.tiendanube_store_id THEN 0
                        ELSE tiendanube_sync_jobs.attempt_count
                    END,
                    max_attempts = EXCLUDED.max_attempts,
                    next_attempt_at = LEAST(tiendanube_sync_jobs.next_attempt_at, EXCLUDED.next_attempt_at),
                    updated_at = EXCLUDED.updated_at
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("bookstoreId", command.bookstoreId())
                .addValue("tiendanubeStoreId", command.tiendanubeStoreId())
                .addValue("storeId", command.storeId())
                .addValue("inventoryId", command.inventoryId())
                .addValue("type", command.type().name())
                .addValue("source", command.source().name())
                .addValue("maxAttempts", maxAttempts)
                .addValue("now", now);

        return jdbcTemplate.queryForObject(sql, parameters, Long.class);
    }
}
