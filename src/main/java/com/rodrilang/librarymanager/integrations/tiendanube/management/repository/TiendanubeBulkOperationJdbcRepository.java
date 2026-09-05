package com.rodrilang.librarymanager.integrations.tiendanube.management.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeBulkOperationItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeBulkOperationResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkAction;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkSelectionType;
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

@Repository
@RequiredArgsConstructor
public class TiendanubeBulkOperationJdbcRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void insertItems(Long operationId, List<Long> inventoryIds, Instant now) {
        if (inventoryIds.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO tiendanube_bulk_operation_items (
                    operation_id,
                    inventory_id,
                    status,
                    created_at,
                    updated_at
                ) VALUES (
                    :operationId,
                    :inventoryId,
                    'PENDING',
                    :now,
                    :now
                )
                ON CONFLICT (operation_id, inventory_id) DO NOTHING
                """;

        Timestamp timestamp = Timestamp.from(now);
        SqlParameterSource[] batch = inventoryIds.stream()
                .map(inventoryId -> new MapSqlParameterSource()
                        .addValue("operationId", operationId)
                        .addValue("inventoryId", inventoryId)
                        .addValue("now", timestamp))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, batch);
    }

    public List<DispatchItem> claimPendingItems(int batchSize) {
        return jdbcTemplate.query("""
                SELECT
                    item.id,
                    item.inventory_id,
                    operation.id AS operation_id,
                    operation.bookstore_id,
                    operation.tiendanube_store_id,
                    operation.store_id,
                    operation.action
                FROM tiendanube_bulk_operation_items item
                JOIN tiendanube_bulk_operations operation ON operation.id = item.operation_id
                JOIN tiendanube_stores store ON store.id = operation.tiendanube_store_id
                WHERE item.status = 'PENDING'
                  AND operation.cancel_requested_at IS NULL
                  AND (
                      operation.action IN ('UNLINK', 'DISABLE_PRICE_SYNC')
                      OR (store.active = TRUE AND store.token_valid = TRUE)
                  )
                ORDER BY item.id
                FOR UPDATE OF item SKIP LOCKED
                LIMIT :batchSize
                """, new MapSqlParameterSource("batchSize", batchSize), (rs, rowNum) -> new DispatchItem(
                rs.getLong("id"),
                rs.getLong("inventory_id"),
                rs.getLong("operation_id"),
                rs.getLong("bookstore_id"),
                rs.getLong("tiendanube_store_id"),
                rs.getLong("store_id"),
                TiendanubeBulkAction.valueOf(rs.getString("action"))
        ));
    }

    public Optional<Long> findCoalescableJobId(
            Long inventoryId,
            String type,
            Long bookstoreId,
            Long tiendanubeStoreId,
            Long storeId
    ) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id
                FROM tiendanube_sync_jobs
                WHERE inventory_id = :inventoryId
                  AND type = :type
                  AND bookstore_id = :bookstoreId
                  AND tiendanube_store_id = :tiendanubeStoreId
                  AND store_id = :storeId
                  AND status IN ('PENDING', 'RETRY_WAIT')
                ORDER BY
                    CASE status WHEN 'PENDING' THEN 0 ELSE 1 END,
                    id DESC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("inventoryId", inventoryId)
                .addValue("type", type)
                .addValue("bookstoreId", bookstoreId)
                .addValue("tiendanubeStoreId", tiendanubeStoreId)
                .addValue("storeId", storeId), Long.class);

        return ids.stream().findFirst();
    }

    public void markQueued(Long itemId, Long jobId, Instant now) {
        jdbcTemplate.update("""
                UPDATE tiendanube_bulk_operation_items
                SET status = 'QUEUED',
                    job_id = :jobId,
                    dispatched_at = :now,
                    updated_at = :now,
                    skip_reason = NULL,
                    skip_message = NULL
                WHERE id = :itemId
                  AND status = 'PENDING'
                """, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("jobId", jobId)
                .addValue("now", Timestamp.from(now)));
    }

    public void markCompleted(Long itemId, Instant now) {
        jdbcTemplate.update("""
                UPDATE tiendanube_bulk_operation_items
                SET status = 'COMPLETED',
                    dispatched_at = COALESCE(dispatched_at, :now),
                    completed_at = :now,
                    updated_at = :now,
                    skip_reason = NULL,
                    skip_message = NULL
                WHERE id = :itemId
                  AND status = 'PENDING'
                """, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("now", Timestamp.from(now)));
    }

    public void markSkipped(Long itemId, String reason, String message, Instant now) {
        jdbcTemplate.update("""
                UPDATE tiendanube_bulk_operation_items
                SET status = 'SKIPPED',
                    skip_reason = :reason,
                    skip_message = :message,
                    completed_at = :now,
                    updated_at = :now
                WHERE id = :itemId
                  AND status = 'PENDING'
                """, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("reason", reason)
                .addValue("message", message)
                .addValue("now", Timestamp.from(now)));
    }

    public int cancelPendingItems(Long operationId, Instant now) {
        return jdbcTemplate.update("""
                UPDATE tiendanube_bulk_operation_items
                SET status = 'CANCELLED',
                    updated_at = :now
                WHERE operation_id = :operationId
                  AND status = 'PENDING'
                """, new MapSqlParameterSource()
                .addValue("operationId", operationId)
                .addValue("now", Timestamp.from(now)));
    }

    public List<Long> findFailedInventoryIds(Long operationId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT item.inventory_id
                FROM tiendanube_bulk_operation_items item
                JOIN tiendanube_sync_jobs job ON job.id = item.job_id
                WHERE item.operation_id = :operationId
                  AND job.status IN ('FAILED', 'BLOCKED')
                ORDER BY item.inventory_id
                """, new MapSqlParameterSource("operationId", operationId), Long.class);
    }

    public Optional<TiendanubeBulkOperationResponse> findOperation(Long operationId, Long bookstoreId) {
        String sql = progressSql("""
                WHERE operation.id = :operationId
                  AND operation.bookstore_id = :bookstoreId
                """);

        List<TiendanubeBulkOperationResponse> result = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("operationId", operationId)
                .addValue("bookstoreId", bookstoreId), (rs, rowNum) -> mapOperation(rs));

        return result.stream().findFirst();
    }

    public Page<TiendanubeBulkOperationResponse> findOperations(Long bookstoreId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        String sql = progressSql("WHERE operation.bookstore_id = :bookstoreId\n") + """
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """;

        long[] total = {0L};
        List<TiendanubeBulkOperationResponse> content = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            total[0] = rs.getLong("total_count");
            return mapOperation(rs);
        });

        return new PageImpl<>(content, pageable, total[0]);
    }

    public Page<TiendanubeBulkOperationItemResponse> findItems(
            Long operationId,
            Long bookstoreId,
            Pageable pageable
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("operationId", operationId)
                .addValue("bookstoreId", bookstoreId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        String sql = """
                SELECT
                    item.id,
                    item.inventory_id,
                    b.title,
                    COALESCE(b.isbn_13, b.isbn_10) AS isbn,
                    item.status AS dispatch_status,
                    item.job_id,
                    job.status AS job_status,
                    job.attempt_count,
                    job.max_attempts,
                    job.last_error_type,
                    job.last_error_message,
                    item.skip_reason,
                    item.skip_message,
                    item.created_at,
                    item.dispatched_at,
                    COALESCE(item.completed_at, job.completed_at) AS completed_at,
                    COUNT(*) OVER() AS total_count
                FROM tiendanube_bulk_operation_items item
                JOIN tiendanube_bulk_operations operation ON operation.id = item.operation_id
                JOIN inventory i ON i.id = item.inventory_id
                JOIN books b ON b.id = i.book_id
                LEFT JOIN tiendanube_sync_jobs job ON job.id = item.job_id
                WHERE item.operation_id = :operationId
                  AND operation.bookstore_id = :bookstoreId
                ORDER BY item.id
                LIMIT :limit OFFSET :offset
                """;

        long[] total = {0L};
        List<TiendanubeBulkOperationItemResponse> content = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            total[0] = rs.getLong("total_count");
            return mapItem(rs);
        });

        return new PageImpl<>(content, pageable, total[0]);
    }

    private String progressSql(String whereClause) {
        return """
                SELECT
                    operation.id,
                    operation.action,
                    operation.selection_type,
                    operation.selection_description,
                    operation.created_at,
                    operation.cancel_requested_at,
                    COUNT(item.id) AS total_items,
                    COUNT(*) FILTER (WHERE item.status = 'PENDING') AS pending_items,
                    COUNT(*) FILTER (WHERE item.status = 'QUEUED' AND job.status = 'PENDING') AS queued_items,
                    COUNT(*) FILTER (WHERE item.status = 'QUEUED' AND job.status = 'PROCESSING') AS processing_items,
                    COUNT(*) FILTER (WHERE item.status = 'QUEUED' AND job.status = 'RETRY_WAIT') AS retrying_items,
                    COUNT(*) FILTER (
                        WHERE item.status = 'COMPLETED'
                           OR (item.status = 'QUEUED' AND job.status = 'COMPLETED')
                    ) AS completed_items,
                    COUNT(*) FILTER (WHERE item.status = 'QUEUED' AND job.status = 'FAILED') AS failed_items,
                    COUNT(*) FILTER (WHERE item.status = 'QUEUED' AND job.status = 'BLOCKED') AS blocked_items,
                    COUNT(*) FILTER (
                        WHERE item.status = 'CANCELLED'
                           OR (item.status = 'QUEUED' AND job.status = 'CANCELLED')
                    ) AS cancelled_items,
                    COUNT(*) FILTER (WHERE item.status = 'SKIPPED') AS skipped_items,
                    GREATEST(
                        operation.created_at,
                        COALESCE(MAX(item.updated_at), operation.created_at),
                        COALESCE(MAX(job.updated_at), operation.created_at)
                    ) AS last_activity_at,
                    COUNT(*) OVER() AS total_count
                FROM tiendanube_bulk_operations operation
                LEFT JOIN tiendanube_bulk_operation_items item ON item.operation_id = operation.id
                LEFT JOIN tiendanube_sync_jobs job ON job.id = item.job_id
                """ + whereClause + """
                GROUP BY operation.id
                """;
    }

    private TiendanubeBulkOperationResponse mapOperation(ResultSet rs) throws SQLException {
        long pendingItems = rs.getLong("pending_items");
        long queuedItems = rs.getLong("queued_items");
        long processingItems = rs.getLong("processing_items");
        long retryingItems = rs.getLong("retrying_items");
        long completedItems = rs.getLong("completed_items");
        long failedItems = rs.getLong("failed_items");
        long blockedItems = rs.getLong("blocked_items");
        long cancelledItems = rs.getLong("cancelled_items");
        long skippedItems = rs.getLong("skipped_items");
        Instant cancelRequestedAt = instant(rs, "cancel_requested_at");

        return new TiendanubeBulkOperationResponse(
                rs.getLong("id"),
                TiendanubeBulkAction.valueOf(rs.getString("action")),
                TiendanubeBulkOperationResponse.resolveStatus(
                        cancelRequestedAt,
                        pendingItems,
                        queuedItems,
                        processingItems,
                        retryingItems,
                        completedItems,
                        failedItems,
                        blockedItems,
                        cancelledItems,
                        skippedItems
                ),
                TiendanubeBulkSelectionType.valueOf(rs.getString("selection_type")),
                rs.getString("selection_description"),
                rs.getLong("total_items"),
                pendingItems,
                queuedItems,
                processingItems,
                retryingItems,
                completedItems,
                failedItems,
                blockedItems,
                cancelledItems,
                skippedItems,
                instant(rs, "created_at"),
                cancelRequestedAt,
                instant(rs, "last_activity_at")
        );
    }

    private TiendanubeBulkOperationItemResponse mapItem(ResultSet rs) throws SQLException {
        return new TiendanubeBulkOperationItemResponse(
                rs.getLong("id"),
                rs.getLong("inventory_id"),
                rs.getString("title"),
                rs.getString("isbn"),
                TiendanubeBulkItemStatus.valueOf(rs.getString("dispatch_status")),
                rs.getObject("job_id", Long.class),
                enumValue(TiendanubeJobStatus.class, rs.getString("job_status")),
                rs.getObject("attempt_count", Integer.class),
                rs.getObject("max_attempts", Integer.class),
                rs.getString("last_error_type"),
                rs.getString("last_error_message"),
                rs.getString("skip_reason"),
                rs.getString("skip_message"),
                instant(rs, "created_at"),
                instant(rs, "dispatched_at"),
                instant(rs, "completed_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value != null ? value.toInstant() : null;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return value != null ? Enum.valueOf(type, value) : null;
    }

    public record DispatchItem(
            Long itemId,
            Long inventoryId,
            Long operationId,
            Long bookstoreId,
            Long tiendanubeStoreId,
            Long storeId,
            TiendanubeBulkAction action
    ) {
    }
}
