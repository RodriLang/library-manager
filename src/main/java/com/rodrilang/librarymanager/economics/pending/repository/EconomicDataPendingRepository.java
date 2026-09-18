package com.rodrilang.librarymanager.economics.pending.repository;

import com.rodrilang.librarymanager.economics.pending.model.EconomicDataPendingReason;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class EconomicDataPendingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EconomicDataPendingSummary summarize(
            Long bookstoreId,
            LocalDate asOf
    ) {
        MapSqlParameterSource params = baseParams(bookstoreId, asOf);
        String sql = BASE_CTE + """
                SELECT
                    COALESCE(SUM(stock_units), 0) AS total_stock_units,
                    COALESCE(SUM(unknown_cost_units), 0) AS unknown_cost_units,
                    COALESCE(SUM(estimated_cost_units), 0) AS estimated_cost_units,
                    COALESCE(SUM(missing_discount_units), 0) AS missing_discount_units,
                    COALESCE(SUM(stock_units) FILTER (WHERE current_editorial_price IS NULL), 0)
                        AS missing_current_price_units,
                    COUNT(*) FILTER (
                        WHERE unknown_cost_units > 0
                           OR missing_discount_units > 0
                           OR current_editorial_price IS NULL
                           OR has_commercial_term = FALSE
                    ) AS pending_book_count,
                    COUNT(*) FILTER (WHERE has_commercial_term = FALSE)
                        AS books_without_commercial_terms
                FROM book_data
                """;

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new EconomicDataPendingSummary(
                rs.getLong("total_stock_units"),
                rs.getLong("unknown_cost_units"),
                rs.getLong("estimated_cost_units"),
                rs.getLong("missing_discount_units"),
                rs.getLong("missing_current_price_units"),
                rs.getLong("pending_book_count"),
                rs.getLong("books_without_commercial_terms")
        ));
    }

    public Page<EconomicDataPendingRow> findPending(
            Long bookstoreId,
            LocalDate asOf,
            EconomicDataPendingReason reason,
            String search,
            Pageable pageable
    ) {
        MapSqlParameterSource params = baseParams(bookstoreId, asOf)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        String filter = pendingFilter(reason, search, params);
        String query = BASE_CTE + """
                SELECT
                    book_id,
                    isbn,
                    title,
                    stock_units,
                    unknown_cost_units,
                    estimated_cost_units,
                    missing_discount_units,
                    current_editorial_price,
                    has_commercial_term
                FROM book_data
                WHERE
                """ + filter + """
                ORDER BY title_sort, book_id
                LIMIT :limit OFFSET :offset
                """;

        String countQuery = BASE_CTE + """
                SELECT COUNT(*)
                FROM book_data
                WHERE
                """ + filter;

        List<EconomicDataPendingRow> rows = jdbcTemplate.query(
                query,
                params,
                (rs, rowNum) -> new EconomicDataPendingRow(
                        rs.getLong("book_id"),
                        rs.getString("isbn"),
                        rs.getString("title"),
                        rs.getLong("stock_units"),
                        rs.getLong("unknown_cost_units"),
                        rs.getLong("estimated_cost_units"),
                        rs.getLong("missing_discount_units"),
                        rs.getBigDecimal("current_editorial_price"),
                        rs.getBoolean("has_commercial_term")
                )
        );

        Long total = jdbcTemplate.queryForObject(countQuery, params, Long.class);
        return new PageImpl<>(rows, pageable, total != null ? total : 0L);
    }

    private String pendingFilter(
            EconomicDataPendingReason reason,
            String search,
            MapSqlParameterSource params
    ) {
        String reasonFilter;
        if (reason == null) {
            reasonFilter = """
                    (
                        unknown_cost_units > 0
                        OR missing_discount_units > 0
                        OR current_editorial_price IS NULL
                        OR has_commercial_term = FALSE
                    )
                    """;
        } else {
            reasonFilter = switch (reason) {
                case UNKNOWN_COST -> "unknown_cost_units > 0";
                case MISSING_DISCOUNT -> "missing_discount_units > 0";
                case MISSING_CURRENT_PRICE -> "current_editorial_price IS NULL";
                case NO_COMMERCIAL_TERM -> "has_commercial_term = FALSE";
            };
        }

        if (search == null || search.isBlank()) {
            return reasonFilter;
        }

        params.addValue("search", "%" + search.trim().toLowerCase() + "%");
        return reasonFilter + """
                AND (
                    LOWER(title) LIKE :search
                    OR LOWER(COALESCE(isbn, '')) LIKE :search
                )
                """;
    }

    private MapSqlParameterSource baseParams(Long bookstoreId, LocalDate asOf) {
        return new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("asOf", asOf);
    }

    private static final String BASE_CTE = """
            WITH current_prices AS (
                SELECT DISTINCT ON (eep.book_id)
                    eep.book_id,
                    eep.price
                FROM effective_editorial_prices eep
                WHERE eep.active = TRUE
                  AND eep.valid_from <= :asOf
                ORDER BY eep.book_id, eep.valid_from DESC, eep.id DESC
            ),
            term_books AS (
                SELECT DISTINCT term.book_id
                FROM bookstore_provider_book_terms term
                JOIN providers provider ON provider.id = term.provider_id
                WHERE term.bookstore_id = :bookstoreId
                  AND term.discount_percentage IS NOT NULL
                  AND provider.active = TRUE
                  AND provider.type = 'COMMERCIAL'
            ),
            book_data AS (
                SELECT
                    inventory.book_id AS book_id,
                    COALESCE(book.isbn_13, book.isbn_10) AS isbn,
                    book.title AS title,
                    LOWER(COALESCE(book.title, '')) AS title_sort,
                    COALESCE(SUM(layer.quantity_remaining), 0) AS stock_units,
                    COALESCE(SUM(layer.quantity_remaining)
                        FILTER (WHERE layer.unit_cost IS NULL), 0) AS unknown_cost_units,
                    COALESCE(SUM(layer.quantity_remaining)
                        FILTER (WHERE layer.cost_type = 'ESTIMATED'), 0) AS estimated_cost_units,
                    COALESCE(SUM(layer.quantity_remaining)
                        FILTER (WHERE layer.discount_percentage IS NULL), 0) AS missing_discount_units,
                    MAX(price.price) AS current_editorial_price,
                    BOOL_OR(term_book.book_id IS NOT NULL) AS has_commercial_term
                FROM inventory_cost_layers layer
                JOIN inventory inventory ON inventory.id = layer.inventory_id
                JOIN books book ON book.id = inventory.book_id
                LEFT JOIN current_prices price ON price.book_id = inventory.book_id
                LEFT JOIN term_books term_book ON term_book.book_id = inventory.book_id
                WHERE inventory.bookstore_id = :bookstoreId
                  AND layer.reversed_at IS NULL
                  AND layer.quantity_remaining > 0
                GROUP BY
                    inventory.book_id,
                    book.isbn_13,
                    book.isbn_10,
                    book.title
            )
            """;
}
