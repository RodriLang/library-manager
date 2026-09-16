package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisRunStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeClaimedImportAnalysisRun;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TiendanubeImportAnalysisRunRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<Long> createRun(
            Long bookstoreId,
            Long tiendanubeStoreId,
            Long storeId,
            Instant now
    ) {
        List<Long> ids = jdbcTemplate.query("""
                INSERT INTO tiendanube_import_analysis_runs (
                    bookstore_id,
                    tiendanube_store_id,
                    store_id,
                    status,
                    created_at,
                    updated_at
                ) VALUES (
                    :bookstoreId,
                    :tiendanubeStoreId,
                    :storeId,
                    'PENDING',
                    :now,
                    :now
                )
                ON CONFLICT DO NOTHING
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("tiendanubeStoreId", tiendanubeStoreId)
                .addValue("storeId", storeId)
                .addValue("now", Timestamp.from(now)), (rs, rowNum) -> rs.getLong("id"));

        return ids.stream().findFirst();
    }

    public Optional<TiendanubeImportAnalysisRunResponse> findActiveRun(
            Long bookstoreId,
            Long tiendanubeStoreId
    ) {
        return queryOne("""
                SELECT *
                FROM tiendanube_import_analysis_runs
                WHERE bookstore_id = :bookstoreId
                  AND tiendanube_store_id = :tiendanubeStoreId
                  AND status IN ('PENDING', 'PROCESSING')
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("tiendanubeStoreId", tiendanubeStoreId), this::mapRun);
    }

    public Optional<TiendanubeClaimedImportAnalysisRun> claimOne(
            Instant now,
            Instant leaseUntil,
            UUID processingToken
    ) {
        return queryOne("""
                WITH candidate AS (
                    SELECT run.id
                    FROM tiendanube_import_analysis_runs run
                    JOIN tiendanube_stores store ON store.id = run.tiendanube_store_id
                    WHERE (
                        run.status = 'PENDING'
                        OR (
                            run.status = 'PROCESSING'
                            AND run.lease_expires_at IS NOT NULL
                            AND run.lease_expires_at < :now
                        )
                    )
                      AND store.active = TRUE
                      AND store.token_valid = TRUE
                      AND store.store_id = run.store_id
                      AND store.bookstore_id = run.bookstore_id
                    ORDER BY run.created_at, run.id
                    FOR UPDATE OF run SKIP LOCKED
                    LIMIT 1
                )
                UPDATE tiendanube_import_analysis_runs run
                SET status = 'PROCESSING',
                    processing_token = :processingToken,
                    processing_started_at = COALESCE(run.processing_started_at, :now),
                    lease_expires_at = :leaseUntil,
                    updated_at = :now,
                    last_error_type = NULL,
                    last_error_message = NULL
                FROM candidate
                WHERE run.id = candidate.id
                RETURNING run.id,
                          run.bookstore_id,
                          run.tiendanube_store_id,
                          run.store_id,
                          run.processing_token
                """, new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("leaseUntil", Timestamp.from(leaseUntil))
                .addValue("processingToken", processingToken), (rs, rowNum) -> new TiendanubeClaimedImportAnalysisRun(
                rs.getLong("id"),
                rs.getLong("bookstore_id"),
                rs.getLong("tiendanube_store_id"),
                rs.getLong("store_id"),
                rs.getObject("processing_token", UUID.class)
        ));
    }

    public Optional<Instant> findNextWakeAt(Instant now) {
        return jdbcTemplate.query("""
                SELECT MIN(next_wake_at) AS next_wake_at
                FROM (
                    SELECT :now AS next_wake_at
                    FROM tiendanube_import_analysis_runs run
                    JOIN tiendanube_stores store ON store.id = run.tiendanube_store_id
                    WHERE run.status = 'PENDING'
                      AND store.active = TRUE
                      AND store.token_valid = TRUE
                      AND store.store_id = run.store_id
                      AND store.bookstore_id = run.bookstore_id

                    UNION ALL

                    SELECT run.lease_expires_at AS next_wake_at
                    FROM tiendanube_import_analysis_runs run
                    JOIN tiendanube_stores store ON store.id = run.tiendanube_store_id
                    WHERE run.status = 'PROCESSING'
                      AND run.lease_expires_at IS NOT NULL
                      AND store.active = TRUE
                      AND store.token_valid = TRUE
                      AND store.store_id = run.store_id
                      AND store.bookstore_id = run.bookstore_id
                ) wakeups
                """, new MapSqlParameterSource("now", Timestamp.from(now)), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            Timestamp value = rs.getTimestamp("next_wake_at");
            return value == null ? Optional.empty() : Optional.of(value.toInstant());
        });
    }

    public boolean markCompleted(Long runId, UUID processingToken, Instant now) {
        int updated = jdbcTemplate.update(countUpdateSql("""
                status = 'COMPLETED',
                processing_token = NULL,
                lease_expires_at = NULL,
                completed_at = :now,
                updated_at = :now,
                last_error_type = NULL,
                last_error_message = NULL,
                """, "AND run.processing_token = :processingToken"), new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("processingToken", processingToken)
                .addValue("now", Timestamp.from(now)));

        return updated == 1;
    }

    public boolean markFailed(
            Long runId,
            UUID processingToken,
            String errorType,
            String errorMessage,
            Instant now
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE tiendanube_import_analysis_runs
                SET status = 'FAILED',
                    processing_token = NULL,
                    lease_expires_at = NULL,
                    completed_at = :now,
                    updated_at = :now,
                    last_error_type = :errorType,
                    last_error_message = :errorMessage
                WHERE id = :runId
                  AND status = 'PROCESSING'
                  AND processing_token = :processingToken
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("processingToken", processingToken)
                .addValue("errorType", errorType)
                .addValue("errorMessage", errorMessage)
                .addValue("now", Timestamp.from(now)));

        return updated == 1;
    }

    public void refreshCounts(Long runId) {
        jdbcTemplate.update(
                countUpdateSql("updated_at = NOW(),", ""),
                new MapSqlParameterSource("runId", runId)
        );
    }

    public Optional<TiendanubeImportAnalysisRunResponse> findRun(Long runId, Long bookstoreId) {
        return queryOne("""
                SELECT *
                FROM tiendanube_import_analysis_runs
                WHERE id = :runId
                  AND bookstore_id = :bookstoreId
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("bookstoreId", bookstoreId), this::mapRun);
    }

    public Page<TiendanubeImportAnalysisRunResponse> findRuns(Long bookstoreId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        List<TiendanubeImportAnalysisRunResponse> content = jdbcTemplate.query("""
                SELECT *
                FROM tiendanube_import_analysis_runs
                WHERE bookstore_id = :bookstoreId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """, params, this::mapRun);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tiendanube_import_analysis_runs
                WHERE bookstore_id = :bookstoreId
                """, new MapSqlParameterSource("bookstoreId", bookstoreId), Long.class);

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private TiendanubeImportAnalysisRunResponse mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new TiendanubeImportAnalysisRunResponse(
                rs.getLong("id"),
                TiendanubeImportAnalysisRunStatus.valueOf(rs.getString("status")),
                rs.getInt("total_count"),
                rs.getInt("ready_to_link_count"),
                rs.getInt("requires_review_count"),
                rs.getInt("not_in_inventory_count"),
                rs.getInt("not_in_catalog_count"),
                rs.getInt("conflict_count"),
                rs.getInt("already_linked_count"),
                rs.getInt("resolved_count"),
                rs.getInt("ignored_count"),
                rs.getInt("missing_identifier_count"),
                rs.getString("last_error_type"),
                rs.getString("last_error_message"),
                instant(rs, "created_at"),
                instant(rs, "completed_at")
        );
    }

    private String countUpdateSql(String extraAssignments, String extraWhere) {
        return """
                WITH counts AS (
                    SELECT
                        COUNT(*) AS total_count,
                        COUNT(*) FILTER (WHERE status = 'READY_TO_LINK') AS ready_to_link_count,
                        COUNT(*) FILTER (WHERE status = 'REQUIRES_REVIEW') AS requires_review_count,
                        COUNT(*) FILTER (WHERE status = 'NOT_IN_INVENTORY') AS not_in_inventory_count,
                        COUNT(*) FILTER (WHERE status = 'NOT_IN_CATALOG') AS not_in_catalog_count,
                        COUNT(*) FILTER (WHERE status = 'CONFLICT') AS conflict_count,
                        COUNT(*) FILTER (WHERE status = 'ALREADY_LINKED') AS already_linked_count,
                        COUNT(*) FILTER (WHERE status = 'RESOLVED') AS resolved_count,
                        COUNT(*) FILTER (WHERE status = 'IGNORED') AS ignored_count,
                        COUNT(*) FILTER (WHERE remote_isbn IS NULL) AS missing_identifier_count
                    FROM tiendanube_import_analysis_items
                    WHERE run_id = :runId
                )
                UPDATE tiendanube_import_analysis_runs run
                SET %s
                    total_count = counts.total_count,
                    ready_to_link_count = counts.ready_to_link_count,
                    requires_review_count = counts.requires_review_count,
                    not_in_inventory_count = counts.not_in_inventory_count,
                    not_in_catalog_count = counts.not_in_catalog_count,
                    conflict_count = counts.conflict_count,
                    already_linked_count = counts.already_linked_count,
                    resolved_count = counts.resolved_count,
                    ignored_count = counts.ignored_count,
                    missing_identifier_count = counts.missing_identifier_count
                FROM counts
                WHERE run.id = :runId
                  AND run.status IN ('PROCESSING', 'COMPLETED')
                  %s
                """.formatted(extraAssignments, extraWhere);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private <T> Optional<T> queryOne(
            String sql,
            SqlParameterSource params,
            RowMapper<T> mapper
    ) {
        List<T> values = jdbcTemplate.query(sql, params, mapper);
        return values.stream().findFirst();
    }
}
