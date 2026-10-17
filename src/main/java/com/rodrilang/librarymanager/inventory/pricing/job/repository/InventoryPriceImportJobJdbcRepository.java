package com.rodrilang.librarymanager.inventory.pricing.job.repository;

import com.rodrilang.librarymanager.inventory.pricing.job.model.InventoryPriceImportJobStatus;
import com.rodrilang.librarymanager.inventory.pricing.job.model.InventoryPriceImportJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InventoryPriceImportJobJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Optional<ClaimedJob> claimNext(InventoryPriceImportJobType type) {
        return jdbcTemplate.query("""
                WITH candidate AS (
                    SELECT id
                    FROM inventory_price_import_jobs
                    WHERE status = 'PENDING'
                      AND job_type = ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM inventory_price_import_jobs running
                          WHERE running.status = 'PROCESSING'
                            AND running.job_type = ?
                      )
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE inventory_price_import_jobs j
                   SET status = 'PROCESSING',
                       started_at = COALESCE(started_at, NOW()),
                       heartbeat_at = NOW(),
                       updated_at = NOW(),
                       error_message = NULL
                  FROM candidate
                 WHERE j.id = candidate.id
                RETURNING j.id, j.import_id, j.job_type, j.processed_rows, j.last_staging_id, j.requested_by_user_id
                """, ps -> { ps.setString(1, type.name()); ps.setString(2, type.name()); }, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
    }

    public void heartbeat(long jobId) {
        jdbcTemplate.update("UPDATE inventory_price_import_jobs SET heartbeat_at = NOW(), updated_at = NOW() WHERE id = ?", jobId);
    }

    private ClaimedJob map(ResultSet rs) throws SQLException {
        return new ClaimedJob(
                rs.getLong("id"),
                rs.getLong("import_id"),
                InventoryPriceImportJobType.valueOf(rs.getString("job_type")),
                rs.getInt("processed_rows"),
                rs.getLong("last_staging_id"),
                (Long) rs.getObject("requested_by_user_id")
        );
    }

    public record ClaimedJob(long id, long importId, InventoryPriceImportJobType type, int processedRows, long lastStagingId, Long requestedByUserId) {}
}
