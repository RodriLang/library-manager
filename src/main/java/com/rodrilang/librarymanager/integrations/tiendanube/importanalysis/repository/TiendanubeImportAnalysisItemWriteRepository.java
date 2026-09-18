package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisCandidateData;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisItemData;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisItemLock;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisReadyItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TiendanubeImportAnalysisItemWriteRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void replaceAnalysis(
            Long runId,
            List<TiendanubeImportAnalysisItemData> items,
            Instant now
    ) {
        jdbcTemplate.update(
                "DELETE FROM tiendanube_import_analysis_items WHERE run_id = :runId",
                new MapSqlParameterSource("runId", runId)
        );

        if (items.isEmpty()) {
            return;
        }

        insertItems(runId, items, now);
        insertCandidates(runId, items, now);
    }

    public Optional<TiendanubeImportAnalysisItemLock> lockItem(
            Long runId,
            Long itemId,
            Long bookstoreId
    ) {
        List<TiendanubeImportAnalysisItemLock> values = jdbcTemplate.query("""
                SELECT
                    item.id,
                    item.run_id,
                    run.bookstore_id,
                    run.tiendanube_store_id,
                    run.store_id,
                    item.product_id,
                    item.variant_id,
                    item.remote_sku,
                    item.status,
                    item.suggested_inventory_id
                FROM tiendanube_import_analysis_items item
                JOIN tiendanube_import_analysis_runs run ON run.id = item.run_id
                WHERE item.id = :itemId
                  AND item.run_id = :runId
                  AND run.bookstore_id = :bookstoreId
                  AND run.status = 'COMPLETED'
                FOR UPDATE OF item
                """, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("runId", runId)
                .addValue("bookstoreId", bookstoreId), (rs, rowNum) -> new TiendanubeImportAnalysisItemLock(
                rs.getLong("id"),
                rs.getLong("run_id"),
                rs.getLong("bookstore_id"),
                rs.getLong("tiendanube_store_id"),
                rs.getLong("store_id"),
                rs.getLong("product_id"),
                rs.getLong("variant_id"),
                rs.getString("remote_sku"),
                TiendanubeImportAnalysisItemStatus.valueOf(rs.getString("status")),
                rs.getObject("suggested_inventory_id", Long.class)
        ));

        return values.stream().findFirst();
    }

    public boolean markResolved(Long itemId, Long inventoryId, Instant now) {
        return jdbcTemplate.update("""
                UPDATE tiendanube_import_analysis_items
                SET status = 'RESOLVED',
                    resolved_inventory_id = :inventoryId,
                    resolution_type = 'LINKED',
                    resolved_at = :now,
                    updated_at = :now,
                    message = NULL
                WHERE id = :itemId
                  AND status NOT IN ('RESOLVED', 'IGNORED', 'ALREADY_LINKED')
                """, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("inventoryId", inventoryId)
                .addValue("now", Timestamp.from(now))) == 1;
    }


    public void markInventoryUnavailableForOtherItems(
            Long runId,
            Long resolvedItemId,
            Long inventoryId,
            Instant now
    ) {
        jdbcTemplate.update("""
                UPDATE tiendanube_import_analysis_candidates candidate
                SET available_for_link = FALSE
                FROM tiendanube_import_analysis_items item
                WHERE candidate.item_id = item.id
                  AND item.run_id = :runId
                  AND item.id <> :resolvedItemId
                  AND candidate.inventory_id = :inventoryId
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("resolvedItemId", resolvedItemId)
                .addValue("inventoryId", inventoryId));

        jdbcTemplate.update("""
                UPDATE tiendanube_import_analysis_items
                SET status = 'CONFLICT',
                    message = 'El inventario sugerido acaba de vincularse con otra publicación de Tiendanube.',
                    updated_at = :now
                WHERE run_id = :runId
                  AND id <> :resolvedItemId
                  AND suggested_inventory_id = :inventoryId
                  AND status IN ('READY_TO_LINK', 'REQUIRES_REVIEW')
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("resolvedItemId", resolvedItemId)
                .addValue("inventoryId", inventoryId)
                .addValue("now", Timestamp.from(now)));
    }

    public boolean markIgnored(Long runId, Long itemId, Long bookstoreId, Instant now) {
        return jdbcTemplate.update("""
                UPDATE tiendanube_import_analysis_items item
                SET status = 'IGNORED',
                    resolution_type = 'IGNORED',
                    resolved_at = :now,
                    updated_at = :now
                FROM tiendanube_import_analysis_runs run
                WHERE item.id = :itemId
                  AND item.run_id = :runId
                  AND run.id = item.run_id
                  AND run.bookstore_id = :bookstoreId
                  AND run.status = 'COMPLETED'
                  AND item.status NOT IN ('RESOLVED', 'IGNORED', 'ALREADY_LINKED')
                """, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("runId", runId)
                .addValue("bookstoreId", bookstoreId)
                .addValue("now", Timestamp.from(now))) == 1;
    }

    public boolean existsCatalogCandidate(Long itemId, Long bookId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM tiendanube_import_analysis_candidates
                    WHERE item_id = :itemId
                      AND book_id = :bookId
                      AND candidate_source = 'CATALOG'
                )
                """, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("bookId", bookId), Boolean.class);

        return Boolean.TRUE.equals(exists);
    }

    public List<TiendanubeImportAnalysisReadyItem> findReadyItems(Long runId, Long bookstoreId) {
        return jdbcTemplate.query("""
                SELECT item.id, item.suggested_inventory_id
                FROM tiendanube_import_analysis_items item
                JOIN tiendanube_import_analysis_runs run ON run.id = item.run_id
                WHERE item.run_id = :runId
                  AND run.bookstore_id = :bookstoreId
                  AND run.status = 'COMPLETED'
                  AND item.status = 'READY_TO_LINK'
                  AND item.suggested_inventory_id IS NOT NULL
                ORDER BY item.id
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("bookstoreId", bookstoreId), (rs, rowNum) -> new TiendanubeImportAnalysisReadyItem(
                rs.getLong("id"),
                rs.getLong("suggested_inventory_id")
        ));
    }

    private void insertItems(
            Long runId,
            List<TiendanubeImportAnalysisItemData> items,
            Instant now
    ) {
        SqlParameterSource[] batch = items.stream()
                .map(item -> new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("productId", item.productId())
                        .addValue("variantId", item.variantId())
                        .addValue("remoteName", item.remoteName())
                        .addValue("remoteSku", item.remoteSku())
                        .addValue("remoteBarcode", item.remoteBarcode())
                        .addValue("remoteIsbn", item.remoteIsbn())
                        .addValue("identifierSource", item.identifierSource())
                        .addValue("identifierRecovered", item.identifierRecovered())
                        .addValue("remotePrice", item.remotePrice())
                        .addValue("remoteStock", item.remoteStock())
                        .addValue("remoteImageUrl", item.remoteImageUrl())
                        .addValue("remotePublished", item.remotePublished())
                        .addValue("status", item.status().name())
                        .addValue("matchType", item.matchType().name())
                        .addValue("suggestedInventoryId", item.suggestedInventoryId())
                        .addValue("suggestedBookId", item.suggestedBookId())
                        .addValue("message", item.message())
                        .addValue("now", Timestamp.from(now)))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate("""
                INSERT INTO tiendanube_import_analysis_items (
                    run_id,
                    product_id,
                    variant_id,
                    remote_name,
                    remote_sku,
                    remote_barcode,
                    remote_isbn,
                    identifier_source,
                    identifier_recovered,
                    remote_price,
                    remote_stock,
                    remote_image_url,
                    remote_published,
                    status,
                    match_type,
                    suggested_inventory_id,
                    suggested_book_id,
                    message,
                    created_at,
                    updated_at
                ) VALUES (
                    :runId,
                    :productId,
                    :variantId,
                    :remoteName,
                    :remoteSku,
                    :remoteBarcode,
                    :remoteIsbn,
                    :identifierSource,
                    :identifierRecovered,
                    :remotePrice,
                    :remoteStock,
                    :remoteImageUrl,
                    :remotePublished,
                    :status,
                    :matchType,
                    :suggestedInventoryId,
                    :suggestedBookId,
                    :message,
                    :now,
                    :now
                )
                """, batch);
    }

    private void insertCandidates(
            Long runId,
            List<TiendanubeImportAnalysisItemData> items,
            Instant now
    ) {
        Map<Long, Long> itemIdByVariantId = loadItemIdsByVariant(runId);
        List<SqlParameterSource> batch = new ArrayList<>();

        for (TiendanubeImportAnalysisItemData item : items) {
            Long itemId = itemIdByVariantId.get(item.variantId());

            if (itemId == null || item.candidates() == null) {
                continue;
            }

            for (TiendanubeImportAnalysisCandidateData candidate : item.candidates()) {
                batch.add(new MapSqlParameterSource()
                        .addValue("itemId", itemId)
                        .addValue("inventoryId", candidate.inventoryId())
                        .addValue("bookId", candidate.bookId())
                        .addValue("rankOrder", candidate.rank())
                        .addValue("candidateSource", candidate.source().name())
                        .addValue("matchType", candidate.matchType().name())
                        .addValue("score", BigDecimal.valueOf(clampScore(candidate.score())))
                        .addValue("availableForLink", candidate.availableForLink())
                        .addValue("now", Timestamp.from(now)));
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO tiendanube_import_analysis_candidates (
                    item_id,
                    inventory_id,
                    book_id,
                    rank_order,
                    candidate_source,
                    match_type,
                    score,
                    available_for_link,
                    created_at
                ) VALUES (
                    :itemId,
                    :inventoryId,
                    :bookId,
                    :rankOrder,
                    :candidateSource,
                    :matchType,
                    :score,
                    :availableForLink,
                    :now
                )
                """, batch.toArray(SqlParameterSource[]::new));
    }

    private Map<Long, Long> loadItemIdsByVariant(Long runId) {
        Map<Long, Long> result = new HashMap<>();

        jdbcTemplate.query("""
                SELECT id, variant_id
                FROM tiendanube_import_analysis_items
                WHERE run_id = :runId
                """, new MapSqlParameterSource("runId", runId), rs -> {
            result.put(rs.getLong("variant_id"), rs.getLong("id"));
        });

        return result;
    }

    private double clampScore(double score) {
        return Math.max(0, Math.min(1, score));
    }
}
