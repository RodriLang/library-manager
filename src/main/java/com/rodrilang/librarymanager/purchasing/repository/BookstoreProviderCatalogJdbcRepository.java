package com.rodrilang.librarymanager.purchasing.repository;

import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookIdentifierStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class BookstoreProviderCatalogJdbcRepository {
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public Map<String, Long> findBooksByExternalCodes(long providerId, Collection<String> externalCodes) {
        if (externalCodes == null || externalCodes.isEmpty()) return Map.of();
        Map<String, Long> result = new HashMap<>();
        RowCallbackHandler handler = rs -> result.put(
                rs.getString("external_code"),
                rs.getLong("book_id")
        );
        namedJdbcTemplate.query("""
                SELECT external_code, book_id
                FROM provider_books
                WHERE provider_id = :providerId AND external_code IN (:codes)
                """, new MapSqlParameterSource().addValue("providerId", providerId).addValue("codes", externalCodes),
                handler);
        return result;
    }

    public void upsertProviderBooks(long providerId, List<ProviderBookUpsert> rows) {
        if (rows.isEmpty()) return;
        Instant now = Instant.now();
        jdbcTemplate.batchUpdate("""
                INSERT INTO provider_books (
                    provider_id, book_id, external_code, reported_isbn, identifier_status,
                    active, first_seen_at, last_seen_at, source, verification_status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, 'BOOKSTORE_IMPORT', 'OBSERVED', ?, ?)
                ON CONFLICT (provider_id, book_id) DO UPDATE SET
                    external_code = COALESCE(provider_books.external_code, EXCLUDED.external_code),
                    reported_isbn = COALESCE(provider_books.reported_isbn, EXCLUDED.reported_isbn),
                    identifier_status = COALESCE(provider_books.identifier_status, EXCLUDED.identifier_status),
                    active = TRUE,
                    last_seen_at = EXCLUDED.last_seen_at,
                    source = CASE WHEN provider_books.verification_status = 'VERIFIED' THEN provider_books.source ELSE 'BOOKSTORE_IMPORT' END,
                    verification_status = CASE WHEN provider_books.verification_status = 'VERIFIED' THEN provider_books.verification_status ELSE 'OBSERVED' END,
                    updated_at = EXCLUDED.updated_at
                """, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, providerId);
            ps.setLong(2, row.bookId());
            ps.setString(3, row.externalCode());
            ps.setString(4, row.reportedIsbn());
            ps.setString(5, row.identifierStatus().name());
            Timestamp ts = Timestamp.from(now);
            ps.setTimestamp(6, ts); ps.setTimestamp(7, ts); ps.setTimestamp(8, ts); ps.setTimestamp(9, ts);
        });
    }



    public int upsertBookstoreTermsFromStaging(long bookstoreId, long providerId, long importId, LocalDate effectiveFrom) {
        return jdbcTemplate.update("""
                INSERT INTO bookstore_provider_book_terms (
                    bookstore_id, provider_id, book_id, latest_list_price, latest_list_effective_from,
                    last_seen_in_price_list_at, last_price_import_id, created_at, updated_at
                )
                SELECT ?, ?, grouped.book_id,
                       CASE WHEN grouped.distinct_prices = 1 THEN grouped.single_price ELSE NULL END,
                       CASE WHEN grouped.distinct_prices = 1 THEN ? ELSE NULL END,
                       ?,
                       CASE WHEN grouped.distinct_prices = 1 THEN ? ELSE NULL END,
                       NOW(), NOW()
                FROM (
                    SELECT book_id,
                           COUNT(DISTINCT incoming_price) FILTER (WHERE incoming_price > 0) AS distinct_prices,
                           MIN(incoming_price) FILTER (WHERE incoming_price > 0) AS single_price
                    FROM inventory_price_import_provider_rows
                    WHERE import_id = ? AND book_id IS NOT NULL
                    GROUP BY book_id
                ) grouped
                ON CONFLICT (bookstore_id, provider_id, book_id) DO UPDATE SET
                    last_seen_in_price_list_at = CASE
                        WHEN bookstore_provider_book_terms.last_seen_in_price_list_at IS NULL
                             OR EXCLUDED.last_seen_in_price_list_at >= bookstore_provider_book_terms.last_seen_in_price_list_at
                        THEN EXCLUDED.last_seen_in_price_list_at
                        ELSE bookstore_provider_book_terms.last_seen_in_price_list_at
                    END,
                    latest_list_price = CASE
                        WHEN EXCLUDED.latest_list_price IS NOT NULL
                             AND (bookstore_provider_book_terms.latest_list_effective_from IS NULL
                                  OR EXCLUDED.latest_list_effective_from >= bookstore_provider_book_terms.latest_list_effective_from)
                        THEN EXCLUDED.latest_list_price
                        ELSE bookstore_provider_book_terms.latest_list_price
                    END,
                    latest_list_effective_from = CASE
                        WHEN EXCLUDED.latest_list_price IS NOT NULL
                             AND (bookstore_provider_book_terms.latest_list_effective_from IS NULL
                                  OR EXCLUDED.latest_list_effective_from >= bookstore_provider_book_terms.latest_list_effective_from)
                        THEN EXCLUDED.latest_list_effective_from
                        ELSE bookstore_provider_book_terms.latest_list_effective_from
                    END,
                    last_price_import_id = CASE
                        WHEN EXCLUDED.latest_list_price IS NOT NULL
                             AND (bookstore_provider_book_terms.latest_list_effective_from IS NULL
                                  OR EXCLUDED.latest_list_effective_from >= bookstore_provider_book_terms.latest_list_effective_from)
                        THEN EXCLUDED.last_price_import_id
                        ELSE bookstore_provider_book_terms.last_price_import_id
                    END,
                    updated_at = NOW()
                """, bookstoreId, providerId, effectiveFrom, effectiveFrom, importId, importId);
    }

    public record ProviderBookUpsert(long bookId, String externalCode, String reportedIsbn,
                                     ProviderBookIdentifierStatus identifierStatus) {}
}
