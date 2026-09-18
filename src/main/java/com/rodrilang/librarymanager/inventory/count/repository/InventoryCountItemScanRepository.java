package com.rodrilang.librarymanager.inventory.count.repository;

import com.rodrilang.librarymanager.inventory.count.dto.internal.InventoryCountItemUpsertCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class InventoryCountItemScanRepository {

    private final JdbcTemplate jdbcTemplate;

    public Long upsertScan(InventoryCountItemUpsertCommand command) {
        OffsetDateTime scannedAt = command.scannedAt().atOffset(ZoneOffset.UTC);

        return jdbcTemplate.queryForObject("""
                         INSERT INTO inventory_count_items (
                            session_id,
                            raw_identifier,
                            normalized_identifier,
                            isbn_10,
                            isbn_13,
                            book_id,
                            catalog_candidate_id,
                            quantity,
                            status,
                            first_scanned_at,
                            last_scanned_at,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)
                        ON CONFLICT (session_id, normalized_identifier)
                        WHERE normalized_identifier IS NOT NULL
                        DO UPDATE SET
                            quantity = inventory_count_items.quantity + 1,
                            isbn_10 = COALESCE(EXCLUDED.isbn_10, inventory_count_items.isbn_10),
                            isbn_13 = COALESCE(EXCLUDED.isbn_13, inventory_count_items.isbn_13),
                            book_id = COALESCE(EXCLUDED.book_id, inventory_count_items.book_id),
                            catalog_candidate_id = COALESCE(inventory_count_items.catalog_candidate_id, EXCLUDED.catalog_candidate_id),
                            status = CASE
                                WHEN EXCLUDED.book_id IS NOT NULL THEN EXCLUDED.status
                                ELSE inventory_count_items.status
                            END,
                            last_scanned_at = EXCLUDED.last_scanned_at,
                            updated_at = EXCLUDED.updated_at
                        RETURNING id
                        """,
                Long.class,
                command.sessionId(),
                command.rawIdentifier(),
                command.normalizedIdentifier(),
                command.isbn10(),
                command.isbn13(),
                command.bookId(),
                command.catalogCandidateId(),
                command.status().name(),
                scannedAt,
                scannedAt,
                scannedAt,
                scannedAt
        );
    }
}
