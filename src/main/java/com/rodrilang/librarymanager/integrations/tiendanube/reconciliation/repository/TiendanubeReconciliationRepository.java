package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeClaimedReconciliationRun;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationIssue;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
public class TiendanubeReconciliationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<Long> createRun(
            Long bookstoreId,
            Long tiendanubeStoreId,
            Long storeId,
            TiendanubeReconciliationSource source,
            Instant now
    ) {
        List<Long> ids = jdbcTemplate.query("""
                INSERT INTO tiendanube_reconciliation_runs (
                    bookstore_id,
                    tiendanube_store_id,
                    store_id,
                    source,
                    status,
                    created_at,
                    updated_at
                ) VALUES (
                    :bookstoreId,
                    :tiendanubeStoreId,
                    :storeId,
                    :source,
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
                .addValue("source", source.name())
                .addValue("now", Timestamp.from(now)), (rs, rowNum) -> rs.getLong("id"));

        return ids.stream().findFirst();
    }

    public Optional<TiendanubeReconciliationRunResponse> findActiveRun(Long bookstoreId, Long tiendanubeStoreId) {
        return queryOne("""
                SELECT *
                FROM tiendanube_reconciliation_runs
                WHERE bookstore_id = :bookstoreId
                  AND tiendanube_store_id = :tiendanubeStoreId
                  AND status IN ('PENDING', 'PROCESSING')
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("tiendanubeStoreId", tiendanubeStoreId), this::mapRun);
    }

    public Optional<TiendanubeClaimedReconciliationRun> claimOne(Instant now, Instant leaseUntil, UUID token) {
        return queryOne("""
                WITH candidate AS (
                    SELECT run.id
                    FROM tiendanube_reconciliation_runs run
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
                UPDATE tiendanube_reconciliation_runs run
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
                .addValue("processingToken", token), (rs, rowNum) -> new TiendanubeClaimedReconciliationRun(
                rs.getLong("id"),
                rs.getLong("bookstore_id"),
                rs.getLong("tiendanube_store_id"),
                rs.getLong("store_id"),
                rs.getObject("processing_token", UUID.class)
        ));
    }

    public List<TiendanubeReconciliationInventorySnapshot> findInventorySnapshots(Long bookstoreId, Long storeId) {
        return jdbcTemplate.query("""
                SELECT
                    i.id AS inventory_id,
                    link.id AS link_id,
                    link.tiendanube_product_id AS product_id,
                    link.tiendanube_variant_id AS variant_id,
                    i.stock AS local_stock,
                    i.sale_price AS local_price,
                    COALESCE(i.tiendanube_price_sync_enabled, FALSE) AS price_sync_enabled
                FROM tiendanube_product_links link
                JOIN inventory i ON i.id = link.inventory_id
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                  AND link.active = TRUE
                  AND link.tiendanube_store_id = :storeId
                ORDER BY link.id
                """, new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("storeId", storeId), (rs, rowNum) -> new TiendanubeReconciliationInventorySnapshot(
                rs.getLong("inventory_id"),
                rs.getLong("link_id"),
                rs.getLong("product_id"),
                rs.getLong("variant_id"),
                integer(rs, "local_stock"),
                rs.getBigDecimal("local_price"),
                rs.getBoolean("price_sync_enabled")
        ));
    }

    public void replaceIssues(Long runId, List<TiendanubeReconciliationIssue> issues, Instant now) {
        jdbcTemplate.update(
                "DELETE FROM tiendanube_reconciliation_items WHERE run_id = :runId",
                new MapSqlParameterSource("runId", runId)
        );

        if (issues.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO tiendanube_reconciliation_items (
                    run_id,
                    inventory_id,
                    link_id,
                    product_id,
                    variant_id,
                    issue_type,
                    local_stock,
                    remote_stock,
                    local_price,
                    remote_price,
                    message,
                    created_at
                ) VALUES (
                    :runId,
                    :inventoryId,
                    :linkId,
                    :productId,
                    :variantId,
                    :issueType,
                    :localStock,
                    :remoteStock,
                    :localPrice,
                    :remotePrice,
                    :message,
                    :createdAt
                )
                """;

        SqlParameterSource[] batch = issues.stream()
                .map(issue -> new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("inventoryId", issue.inventoryId())
                        .addValue("linkId", issue.linkId())
                        .addValue("productId", issue.productId())
                        .addValue("variantId", issue.variantId())
                        .addValue("issueType", issue.issueType().name())
                        .addValue("localStock", issue.localStock())
                        .addValue("remoteStock", issue.remoteStock())
                        .addValue("localPrice", issue.localPrice())
                        .addValue("remotePrice", issue.remotePrice())
                        .addValue("message", issue.message())
                        .addValue("createdAt", Timestamp.from(now)))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, batch);
    }

    public boolean markCompleted(
            Long runId,
            UUID processingToken,
            int linkedCount,
            int checkedCount,
            int issueCount,
            Instant now
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE tiendanube_reconciliation_runs
                SET status = 'COMPLETED',
                    linked_count = :linkedCount,
                    checked_count = :checkedCount,
                    issue_count = :issueCount,
                    processing_token = NULL,
                    lease_expires_at = NULL,
                    completed_at = :now,
                    updated_at = :now,
                    last_error_type = NULL,
                    last_error_message = NULL
                WHERE id = :runId
                  AND status = 'PROCESSING'
                  AND processing_token = :processingToken
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("processingToken", processingToken)
                .addValue("linkedCount", linkedCount)
                .addValue("checkedCount", checkedCount)
                .addValue("issueCount", issueCount)
                .addValue("now", Timestamp.from(now)));

        return updated == 1;
    }

    public boolean markFailed(
            Long runId,
            UUID processingToken,
            int linkedCount,
            String errorType,
            String errorMessage,
            Instant now
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE tiendanube_reconciliation_runs
                SET status = 'FAILED',
                    linked_count = :linkedCount,
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
                .addValue("linkedCount", linkedCount)
                .addValue("errorType", errorType)
                .addValue("errorMessage", errorMessage)
                .addValue("now", Timestamp.from(now)));

        return updated == 1;
    }

    public Optional<TiendanubeReconciliationRunResponse> findRun(Long runId, Long bookstoreId) {
        return queryOne("""
                SELECT *
                FROM tiendanube_reconciliation_runs
                WHERE id = :runId
                  AND bookstore_id = :bookstoreId
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("bookstoreId", bookstoreId), this::mapRun);
    }

    public Page<TiendanubeReconciliationRunResponse> findRuns(Long bookstoreId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        List<TiendanubeReconciliationRunResponse> content = jdbcTemplate.query("""
                SELECT *
                FROM tiendanube_reconciliation_runs
                WHERE bookstore_id = :bookstoreId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """, params, this::mapRun);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tiendanube_reconciliation_runs
                WHERE bookstore_id = :bookstoreId
                """, new MapSqlParameterSource("bookstoreId", bookstoreId), Long.class);

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    public Page<TiendanubeReconciliationItemResponse> findItems(Long runId, Long bookstoreId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("bookstoreId", bookstoreId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        List<TiendanubeReconciliationItemResponse> content = jdbcTemplate.query("""
                SELECT
                    item.id,
                    item.inventory_id,
                    b.title,
                    COALESCE(b.isbn_13, b.isbn_10) AS isbn,
                    item.link_id,
                    item.issue_type,
                    item.product_id,
                    item.variant_id,
                    item.local_stock,
                    item.remote_stock,
                    item.local_price,
                    item.remote_price,
                    item.message,
                    item.created_at
                FROM tiendanube_reconciliation_items item
                JOIN tiendanube_reconciliation_runs run ON run.id = item.run_id
                JOIN inventory i ON i.id = item.inventory_id
                JOIN books b ON b.id = i.book_id
                WHERE item.run_id = :runId
                  AND run.bookstore_id = :bookstoreId
                ORDER BY item.id
                LIMIT :limit OFFSET :offset
                """, params, this::mapItem);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tiendanube_reconciliation_items item
                JOIN tiendanube_reconciliation_runs run ON run.id = item.run_id
                WHERE item.run_id = :runId
                  AND run.bookstore_id = :bookstoreId
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("bookstoreId", bookstoreId), Long.class);

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private TiendanubeReconciliationRunResponse mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new TiendanubeReconciliationRunResponse(
                rs.getLong("id"),
                rs.getLong("store_id"),
                TiendanubeReconciliationSource.valueOf(rs.getString("source")),
                TiendanubeReconciliationStatus.valueOf(rs.getString("status")),
                rs.getInt("linked_count"),
                rs.getInt("checked_count"),
                rs.getInt("issue_count"),
                rs.getString("last_error_type"),
                rs.getString("last_error_message"),
                instant(rs, "created_at"),
                instant(rs, "processing_started_at"),
                instant(rs, "completed_at")
        );
    }

    private TiendanubeReconciliationItemResponse mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new TiendanubeReconciliationItemResponse(
                rs.getLong("id"),
                rs.getLong("inventory_id"),
                rs.getString("title"),
                rs.getString("isbn"),
                rs.getLong("link_id"),
                TiendanubeReconciliationIssueType.valueOf(rs.getString("issue_type")),
                rs.getLong("product_id"),
                rs.getLong("variant_id"),
                integer(rs, "local_stock"),
                integer(rs, "remote_stock"),
                rs.getBigDecimal("local_price"),
                rs.getBigDecimal("remote_price"),
                rs.getString("message"),
                instant(rs, "created_at")
        );
    }

    private Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private <T> Optional<T> queryOne(
            String sql,
            MapSqlParameterSource params,
            org.springframework.jdbc.core.RowMapper<T> mapper
    ) {
        List<T> rows = jdbcTemplate.query(sql, params, mapper);
        return rows.stream().findFirst();
    }
}
