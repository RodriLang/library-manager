package com.rodrilang.librarymanager.integrations.tiendanube.management.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeInventoryFilterRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeManagedInventoryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeManagementSummaryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminPublicationFilter;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminStockFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class TiendanubeManagementInventoryRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "title", "b.title_sort",
            "publisher", "p.name",
            "stock", "i.stock",
            "salePrice", "i.sale_price",
            "status", "i.tiendanube_status",
            "lastSyncedAt", "link.last_synced_at"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Page<TiendanubeManagedInventoryResponse> search(
            Long bookstoreId,
            TiendanubeInventoryFilterRequest filter,
            Pageable pageable
    ) {
        MapSqlParameterSource params = parameters(bookstoreId, filter)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        String sql = """
                SELECT
                    i.id AS inventory_id,
                    i.book_id,
                    b.title,
                    authors.author_names,
                    p.id AS publisher_id,
                    p.name AS publisher_name,
                    COALESCE(b.isbn_13, b.isbn_10) AS isbn,
                    b.cover_url,
                    i.stock,
                    i.minimum_stock,
                    i.sale_price,
                    i.condition,
                    i.tiendanube_status,
                    COALESCE(i.tiendanube_price_sync_enabled, FALSE) AS price_sync_enabled,
                    link.id AS link_id,
                    link.tiendanube_product_id,
                    link.tiendanube_variant_id,
                    link.sku,
                    link.last_synced_at,
                    link.last_error,
                    latest_job.id AS latest_job_id,
                    latest_job.type AS latest_job_type,
                    latest_job.status AS latest_job_status,
                    latest_job.last_error_type AS latest_job_error_type,
                    latest_job.last_error_message AS latest_job_error_message,
                    latest_job.created_at AS latest_job_created_at,
                    COUNT(*) OVER() AS total_count
                FROM inventory i
                JOIN books b ON b.id = i.book_id
                LEFT JOIN publishers p ON p.id = b.publisher_id
                LEFT JOIN tiendanube_stores current_store
                       ON current_store.bookstore_id = i.bookstore_id
                      AND current_store.active = TRUE
                LEFT JOIN LATERAL (
                    SELECT STRING_AGG(a.name, '|' ORDER BY a.name) AS author_names
                    FROM book_authors ba
                    JOIN authors a ON a.id = ba.author_id
                    WHERE ba.book_id = b.id
                ) authors ON TRUE
                LEFT JOIN tiendanube_product_links link
                       ON link.inventory_id = i.id
                      AND link.active = TRUE
                      AND link.tiendanube_store_id = current_store.store_id
                LEFT JOIN LATERAL (
                    SELECT job.id, job.type, job.status, job.last_error_type, job.last_error_message, job.created_at
                    FROM tiendanube_sync_jobs job
                    WHERE job.inventory_id = i.id
                      AND job.tiendanube_store_id = current_store.id
                      AND job.store_id = current_store.store_id
                    ORDER BY
                        CASE WHEN job.status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT') THEN 0 ELSE 1 END,
                        job.created_at DESC,
                        job.id DESC
                    LIMIT 1
                ) latest_job ON TRUE
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                """ + filterSql(filter)
                + "\nORDER BY " + orderBy(pageable)
                + "\nLIMIT :limit OFFSET :offset";

        long[] total = {0L};
        List<TiendanubeManagedInventoryResponse> content = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            total[0] = rs.getLong("total_count");
            return mapInventory(rs);
        });

        return new PageImpl<>(content, pageable, total[0]);
    }


    public TiendanubeManagementSummaryResponse summary(Long bookstoreId) {
        return jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE link.id IS NOT NULL) AS published,
                    COUNT(*) FILTER (
                        WHERE link.id IS NULL
                          AND i.tiendanube_status NOT IN (
                              'PENDING_PUBLICATION', 'PUBLISHING', 'LINK_REQUIRED',
                              'SYNC_ERROR', 'REMOTE_PRODUCT_NOT_FOUND'
                          )
                    ) AS not_published,
                    COUNT(*) FILTER (
                        WHERE i.tiendanube_status IN ('PENDING_PUBLICATION', 'PUBLISHING')
                           OR latest_job.status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT')
                    ) AS pending,
                    COUNT(*) FILTER (
                        WHERE i.tiendanube_status = 'SYNC_ERROR'
                           OR latest_job.status IN ('FAILED', 'BLOCKED')
                    ) AS errors,
                    COUNT(*) FILTER (WHERE i.tiendanube_status = 'REMOTE_PRODUCT_NOT_FOUND') AS remote_not_found,
                    COUNT(*) FILTER (WHERE COALESCE(i.tiendanube_price_sync_enabled, FALSE)) AS price_sync_enabled
                FROM inventory i
                LEFT JOIN tiendanube_stores current_store ON current_store.bookstore_id = i.bookstore_id
                LEFT JOIN tiendanube_product_links link
                       ON link.inventory_id = i.id
                      AND link.active = TRUE
                      AND link.tiendanube_store_id = current_store.store_id
                LEFT JOIN LATERAL (
                    SELECT job.status
                    FROM tiendanube_sync_jobs job
                    WHERE job.inventory_id = i.id
                      AND job.tiendanube_store_id = current_store.id
                      AND job.store_id = current_store.store_id
                    ORDER BY
                        CASE WHEN job.status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT') THEN 0 ELSE 1 END,
                        job.created_at DESC,
                        job.id DESC
                    LIMIT 1
                ) latest_job ON TRUE
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                """, new MapSqlParameterSource("bookstoreId", bookstoreId), (rs, rowNum) ->
                new TiendanubeManagementSummaryResponse(
                        rs.getLong("total"),
                        rs.getLong("published"),
                        rs.getLong("not_published"),
                        rs.getLong("pending"),
                        rs.getLong("errors"),
                        rs.getLong("remote_not_found"),
                        rs.getLong("price_sync_enabled")
                ));
    }

    public List<Long> findMatchingInventoryIds(Long bookstoreId, TiendanubeInventoryFilterRequest filter) {
        MapSqlParameterSource params = parameters(bookstoreId, filter);

        String sql = """
                SELECT i.id
                FROM inventory i
                JOIN books b ON b.id = i.book_id
                LEFT JOIN publishers p ON p.id = b.publisher_id
                LEFT JOIN tiendanube_stores current_store
                       ON current_store.bookstore_id = i.bookstore_id
                      AND current_store.active = TRUE
                LEFT JOIN tiendanube_product_links link
                       ON link.inventory_id = i.id
                      AND link.active = TRUE
                      AND link.tiendanube_store_id = current_store.store_id
                LEFT JOIN LATERAL (
                    SELECT job.status
                    FROM tiendanube_sync_jobs job
                    WHERE job.inventory_id = i.id
                      AND job.tiendanube_store_id = current_store.id
                      AND job.store_id = current_store.store_id
                    ORDER BY
                        CASE WHEN job.status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT') THEN 0 ELSE 1 END,
                        job.created_at DESC,
                        job.id DESC
                    LIMIT 1
                ) latest_job ON TRUE
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                """ + filterSql(filter)
                + "\nORDER BY i.id";

        return jdbcTemplate.queryForList(sql, params, Long.class);
    }

    public List<Long> findExistingInventoryIds(Long bookstoreId, Set<Long> inventoryIds) {
        if (inventoryIds == null || inventoryIds.isEmpty()) {
            return List.of();
        }

        return jdbcTemplate.queryForList("""
                SELECT id
                FROM inventory
                WHERE bookstore_id = :bookstoreId
                  AND active = TRUE
                  AND id IN (:inventoryIds)
                ORDER BY id
                """, new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("inventoryIds", inventoryIds), Long.class);
    }

    private MapSqlParameterSource parameters(Long bookstoreId, TiendanubeInventoryFilterRequest filter) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("bookstoreId", bookstoreId);

        if (filter.q() != null) {
            params.addValue("query", "%" + filter.q().toLowerCase() + "%");
        }

        if (filter.publisherId() != null) {
            params.addValue("publisherId", filter.publisherId());
        }

        if (filter.priceSyncEnabled() != null) {
            params.addValue("priceSyncEnabled", filter.priceSyncEnabled());
        }

        return params;
    }

    private String filterSql(TiendanubeInventoryFilterRequest filter) {
        StringBuilder sql = new StringBuilder();

        if (filter.q() != null) {
            sql.append("""
                      AND (
                          LOWER(b.title) LIKE :query
                          OR LOWER(COALESCE(b.isbn_13, '')) LIKE :query
                          OR LOWER(COALESCE(b.isbn_10, '')) LIKE :query
                          OR LOWER(COALESCE(p.name, '')) LIKE :query
                          OR LOWER(COALESCE(link.sku, '')) LIKE :query
                          OR EXISTS (
                              SELECT 1
                              FROM book_authors search_ba
                              JOIN authors search_author ON search_author.id = search_ba.author_id
                              WHERE search_ba.book_id = b.id
                                AND LOWER(search_author.name) LIKE :query
                          )
                      )
                    """);
        }

        if (filter.publisherId() != null) {
            sql.append(" AND b.publisher_id = :publisherId\n");
        }

        if (filter.priceSyncEnabled() != null) {
            sql.append(" AND COALESCE(i.tiendanube_price_sync_enabled, FALSE) = :priceSyncEnabled\n");
        }

        appendPublicationFilter(sql, filter.publication());
        appendStockFilter(sql, filter.stock());
        return sql.toString();
    }

    private void appendPublicationFilter(StringBuilder sql, TiendanubeAdminPublicationFilter filter) {
        switch (filter) {
            case ALL -> {
            }
            case PUBLISHED -> sql.append(" AND link.id IS NOT NULL\n");
            case NOT_PUBLISHED -> sql.append("""
                     AND link.id IS NULL
                     AND i.tiendanube_status NOT IN (
                         'PENDING_PUBLICATION', 'PUBLISHING', 'LINK_REQUIRED',
                         'SYNC_ERROR', 'REMOTE_PRODUCT_NOT_FOUND'
                     )
                    """);
            case PENDING -> sql.append("""
                     AND (
                         i.tiendanube_status IN ('PENDING_PUBLICATION', 'PUBLISHING')
                         OR latest_job.status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT')
                     )
                    """);
            case ERROR -> sql.append("""
                     AND (
                         i.tiendanube_status = 'SYNC_ERROR'
                         OR latest_job.status IN ('FAILED', 'BLOCKED')
                     )
                    """);
            case REMOTE_NOT_FOUND -> sql.append(" AND i.tiendanube_status = 'REMOTE_PRODUCT_NOT_FOUND'\n");
        }
    }

    private void appendStockFilter(StringBuilder sql, TiendanubeAdminStockFilter filter) {
        switch (filter) {
            case ALL -> {
            }
            case AVAILABLE -> sql.append(" AND i.stock > i.minimum_stock\n");
            case LOW -> sql.append(" AND i.stock > 0 AND i.stock <= i.minimum_stock\n");
            case OUT -> sql.append(" AND i.stock = 0\n");
        }
    }

    private String orderBy(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "b.title_sort ASC, i.id ASC";
        }

        List<String> orders = pageable.getSort().stream()
                .map(order -> {
                    String column = SORT_COLUMNS.get(order.getProperty());
                    return column != null ? column + (order.isAscending() ? " ASC" : " DESC") : null;
                })
                .filter(Objects::nonNull)
                .toList();

        return orders.isEmpty() ? "b.title_sort ASC, i.id ASC" : String.join(", ", orders) + ", i.id ASC";
    }

    private TiendanubeManagedInventoryResponse mapInventory(ResultSet rs) throws SQLException {
        return new TiendanubeManagedInventoryResponse(
                rs.getLong("inventory_id"),
                rs.getLong("book_id"),
                rs.getString("title"),
                splitAuthors(rs.getString("author_names")),
                rs.getObject("publisher_id", Long.class),
                rs.getString("publisher_name"),
                rs.getString("isbn"),
                rs.getString("cover_url"),
                rs.getObject("stock", Integer.class),
                rs.getObject("minimum_stock", Integer.class),
                rs.getBigDecimal("sale_price"),
                rs.getString("condition"),
                enumValue(TiendanubeInventoryStatus.class, rs.getString("tiendanube_status")),
                rs.getObject("link_id") != null,
                rs.getBoolean("price_sync_enabled"),
                rs.getObject("tiendanube_product_id", Long.class),
                rs.getObject("tiendanube_variant_id", Long.class),
                rs.getString("sku"),
                instant(rs, "last_synced_at"),
                rs.getString("last_error"),
                rs.getObject("latest_job_id", Long.class),
                enumValue(TiendanubeJobType.class, rs.getString("latest_job_type")),
                enumValue(TiendanubeJobStatus.class, rs.getString("latest_job_status")),
                rs.getString("latest_job_error_type"),
                rs.getString("latest_job_error_message"),
                instant(rs, "latest_job_created_at")
        );
    }

    private List<String> splitAuthors(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(author -> !author.isBlank())
                .toList();
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value != null ? value.toInstant() : null;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return value != null ? Enum.valueOf(type, value) : null;
    }
}
