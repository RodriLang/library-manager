package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisCandidateResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisCandidateSource;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisResolutionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TiendanubeImportAnalysisQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Page<TiendanubeImportAnalysisItemResponse> findItems(
            Long runId,
            Long bookstoreId,
            TiendanubeImportAnalysisItemStatus status,
            TiendanubeImportAnalysisMatchType matchType,
            Boolean missingIdentifier,
            String query,
            Pageable pageable
    ) {
        StringBuilder where = new StringBuilder("""
                FROM tiendanube_import_analysis_items item
                JOIN tiendanube_import_analysis_runs run ON run.id = item.run_id
                WHERE item.run_id = :runId
                  AND run.bookstore_id = :bookstoreId
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("bookstoreId", bookstoreId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        appendFilters(where, params, status, matchType, missingIdentifier, query);

        List<TiendanubeImportAnalysisItemResponse> items = jdbcTemplate.query(
                "SELECT item.* " + where + " ORDER BY item.id LIMIT :limit OFFSET :offset",
                params,
                this::mapItem
        );

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + where,
                params,
                Long.class
        );

        return new PageImpl<>(
                attachCandidates(items),
                pageable,
                total == null ? 0L : total
        );
    }

    public Optional<TiendanubeImportAnalysisItemResponse> findItem(
            Long runId,
            Long itemId,
            Long bookstoreId
    ) {
        List<TiendanubeImportAnalysisItemResponse> values = jdbcTemplate.query("""
                SELECT item.*
                FROM tiendanube_import_analysis_items item
                JOIN tiendanube_import_analysis_runs run ON run.id = item.run_id
                WHERE item.id = :itemId
                  AND item.run_id = :runId
                  AND run.bookstore_id = :bookstoreId
                """, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("runId", runId)
                .addValue("bookstoreId", bookstoreId), this::mapItem);

        if (values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(attachCandidates(values).getFirst());
    }

    private void appendFilters(
            StringBuilder where,
            MapSqlParameterSource params,
            TiendanubeImportAnalysisItemStatus status,
            TiendanubeImportAnalysisMatchType matchType,
            Boolean missingIdentifier,
            String query
    ) {
        if (status != null) {
            where.append(" AND item.status = :status");
            params.addValue("status", status.name());
        }

        if (matchType != null) {
            where.append(" AND item.match_type = :matchType");
            params.addValue("matchType", matchType.name());
        }

        if (missingIdentifier != null) {
            where.append(Boolean.TRUE.equals(missingIdentifier)
                    ? " AND item.remote_isbn IS NULL"
                    : " AND item.remote_isbn IS NOT NULL");
        }

        if (StringUtils.hasText(query)) {
            where.append("""
                     AND (
                        item.remote_name ILIKE :query
                        OR item.remote_sku ILIKE :query
                        OR item.remote_barcode ILIKE :query
                        OR item.remote_isbn ILIKE :query
                     )
                    """);
            params.addValue("query", "%" + query.trim() + "%");
        }
    }

    private List<TiendanubeImportAnalysisItemResponse> attachCandidates(
            List<TiendanubeImportAnalysisItemResponse> items
    ) {
        if (items.isEmpty()) {
            return items;
        }

        List<Long> itemIds = items.stream()
                .map(TiendanubeImportAnalysisItemResponse::id)
                .toList();

        Map<Long, List<TiendanubeImportAnalysisCandidateResponse>> byItem = findCandidates(itemIds);

        return items.stream()
                .map(item -> withCandidates(item, byItem.getOrDefault(item.id(), List.of())))
                .toList();
    }

    private Map<Long, List<TiendanubeImportAnalysisCandidateResponse>> findCandidates(List<Long> itemIds) {
        Map<Long, List<TiendanubeImportAnalysisCandidateResponse>> result = new HashMap<>();

        jdbcTemplate.query("""
                SELECT
                    candidate.item_id,
                    candidate.inventory_id,
                    candidate.book_id,
                    candidate.rank_order,
                    candidate.candidate_source,
                    candidate.match_type,
                    candidate.score,
                    candidate.available_for_link,
                    COALESCE(book.isbn_13, book.isbn_10) AS isbn,
                    book.title,
                    publisher.name AS publisher,
                    book.cover_url,
                    inventory.condition,
                    inventory.stock,
                    inventory.sale_price,
                    authors.names AS authors
                FROM tiendanube_import_analysis_candidates candidate
                JOIN books book ON book.id = candidate.book_id
                LEFT JOIN inventory ON inventory.id = candidate.inventory_id
                LEFT JOIN publishers publisher ON publisher.id = book.publisher_id
                LEFT JOIN LATERAL (
                    SELECT string_agg(author.name, ', ' ORDER BY author.name) AS names
                    FROM book_authors book_author
                    JOIN authors author ON author.id = book_author.author_id
                    WHERE book_author.book_id = book.id
                ) authors ON TRUE
                WHERE candidate.item_id IN (:itemIds)
                ORDER BY candidate.item_id, candidate.rank_order, candidate.id
                """, new MapSqlParameterSource("itemIds", itemIds), rs -> {
            Long itemId = rs.getLong("item_id");
            result.computeIfAbsent(itemId, ignored -> new ArrayList<>())
                    .add(mapCandidate(rs));
        });

        return result;
    }

    private TiendanubeImportAnalysisCandidateResponse mapCandidate(ResultSet rs) throws SQLException {
        String condition = rs.getString("condition");

        return new TiendanubeImportAnalysisCandidateResponse(
                rs.getObject("inventory_id", Long.class),
                rs.getLong("book_id"),
                rs.getString("isbn"),
                rs.getString("title"),
                rs.getString("authors"),
                rs.getString("publisher"),
                rs.getString("cover_url"),
                condition == null ? null : BookCondition.valueOf(condition),
                rs.getObject("stock", Integer.class),
                rs.getBigDecimal("sale_price"),
                rs.getInt("rank_order"),
                rs.getBigDecimal("score").doubleValue(),
                TiendanubeImportAnalysisCandidateSource.valueOf(rs.getString("candidate_source")),
                TiendanubeImportAnalysisMatchType.valueOf(rs.getString("match_type")),
                rs.getBoolean("available_for_link")
        );
    }

    private TiendanubeImportAnalysisItemResponse mapItem(ResultSet rs, int rowNum) throws SQLException {
        String resolutionType = rs.getString("resolution_type");

        return new TiendanubeImportAnalysisItemResponse(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getLong("variant_id"),
                rs.getString("remote_name"),
                rs.getString("remote_sku"),
                rs.getString("remote_barcode"),
                rs.getString("remote_isbn"),
                rs.getString("identifier_source"),
                rs.getBoolean("identifier_recovered"),
                rs.getBigDecimal("remote_price"),
                rs.getObject("remote_stock", Integer.class),
                rs.getString("remote_image_url"),
                rs.getObject("remote_published", Boolean.class),
                TiendanubeImportAnalysisItemStatus.valueOf(rs.getString("status")),
                TiendanubeImportAnalysisMatchType.valueOf(rs.getString("match_type")),
                rs.getObject("suggested_inventory_id", Long.class),
                rs.getObject("suggested_book_id", Long.class),
                rs.getObject("resolved_inventory_id", Long.class),
                resolutionType == null ? null : TiendanubeImportAnalysisResolutionType.valueOf(resolutionType),
                rs.getString("message"),
                instant(rs, "resolved_at"),
                List.of()
        );
    }

    private TiendanubeImportAnalysisItemResponse withCandidates(
            TiendanubeImportAnalysisItemResponse item,
            List<TiendanubeImportAnalysisCandidateResponse> candidates
    ) {
        return new TiendanubeImportAnalysisItemResponse(
                item.id(),
                item.productId(),
                item.variantId(),
                item.remoteName(),
                item.remoteSku(),
                item.remoteBarcode(),
                item.remoteIsbn(),
                item.identifierSource(),
                item.identifierRecovered(),
                item.remotePrice(),
                item.remoteStock(),
                item.remoteImageUrl(),
                item.remotePublished(),
                item.status(),
                item.matchType(),
                item.suggestedInventoryId(),
                item.suggestedBookId(),
                item.resolvedInventoryId(),
                item.resolutionType(),
                item.message(),
                item.resolvedAt(),
                candidates
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
