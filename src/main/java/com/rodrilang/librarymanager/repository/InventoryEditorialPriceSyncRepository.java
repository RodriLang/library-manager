package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.dto.internal.InventoryEditorialPriceSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class InventoryEditorialPriceSyncRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryEditorialPriceSyncResult syncCurrentPrices(Collection<Long> bookIds, LocalDate currentDate) {
        if (bookIds == null || bookIds.isEmpty()) {
            return new InventoryEditorialPriceSyncResult(0, List.of());
        }

        String sql = """
                UPDATE inventory i
                SET sale_price = current_price.price,
                    updated_at = clock_timestamp()
                FROM (
                    SELECT DISTINCT ON (eep.book_id)
                           eep.book_id,
                           eep.price
                    FROM effective_editorial_prices eep
                    WHERE eep.book_id IN (:bookIds)
                      AND eep.active = TRUE
                      AND eep.valid_from <= :currentDate
                    ORDER BY eep.book_id, eep.valid_from DESC, eep.id DESC
                ) current_price
                WHERE i.book_id = current_price.book_id
                  AND i.editorial_price_sync_enabled = TRUE
                  AND i.active = TRUE
                  AND i.sale_price IS DISTINCT FROM current_price.price
                RETURNING i.id, i.tiendanube_price_sync_enabled, i.tiendanube_status
                """;

        long startedAt = System.nanoTime();

        List<SyncedInventoryRow> updated = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("bookIds", bookIds).addValue("currentDate", currentDate),
                (rs, rowNum) -> new SyncedInventoryRow(
                        rs.getLong("id"),
                        rs.getBoolean("tiendanube_price_sync_enabled"),
                        rs.getString("tiendanube_status")
                )
        );

        List<Long> tiendanubeSyncInventoryIds = updated.stream()
                .filter(SyncedInventoryRow::tiendanubePriceSyncEnabled)
                .filter(row -> "LINKED".equals(row.tiendanubeStatus()))
                .map(SyncedInventoryRow::inventoryId)
                .toList();

        log.info(
                "Inventory editorial price sync completed. books={} updatedInventories={} tiendanubeSync={} time={}ms",
                bookIds.size(),
                updated.size(),
                tiendanubeSyncInventoryIds.size(),
                (System.nanoTime() - startedAt) / 1_000_000
        );

        return new InventoryEditorialPriceSyncResult(updated.size(), tiendanubeSyncInventoryIds);
    }

    private record SyncedInventoryRow(Long inventoryId, boolean tiendanubePriceSyncEnabled, String tiendanubeStatus) {
    }
}