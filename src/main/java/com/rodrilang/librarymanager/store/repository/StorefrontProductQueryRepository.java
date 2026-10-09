package com.rodrilang.librarymanager.store.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class StorefrontProductQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public Result find(
            Long storeId,
            String q,
            List<Long> authorIds,
            List<Long> publisherIds,
            String category,
            String genre,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            Boolean featured,
            String sort,
            int page,
            int size,
            LocalDate today
    ) {
        StringBuilder from = new StringBuilder("""
                FROM store_publications sp
                JOIN inventory i ON i.id = sp.inventory_id
                JOIN books b ON b.id = i.book_id
                LEFT JOIN publishers pub ON pub.id = b.publisher_id
                JOIN LATERAL (
                    SELECT ip.amount
                    FROM inventory_prices ip
                    WHERE ip.inventory_id = i.id
                      AND ip.effective_from <= :today
                    ORDER BY ip.effective_from DESC, ip.id DESC
                    LIMIT 1
                ) current_price ON TRUE
                LEFT JOIN LATERAL (
                    SELECT COALESCE(SUM(r.quantity), 0) AS quantity
                    FROM store_stock_reservations r
                    WHERE r.inventory_id = i.id
                      AND r.status = 'ACTIVE'
                      AND (r.expires_at IS NULL OR r.expires_at > CURRENT_TIMESTAMP)
                ) active_reservations ON TRUE
                WHERE sp.store_id = :storeId
                  AND sp.published = TRUE
                  AND i.active = TRUE
                  AND b.active = TRUE
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("today", today);

        String normalizedQ = normalize(q);
        if (normalizedQ != null) {
            from.append("""
                    AND (
                        LOWER(b.title) LIKE :qLike
                        OR LOWER(COALESCE(b.isbn_13, '')) LIKE :qLike
                        OR LOWER(COALESCE(b.isbn_10, '')) LIKE :qLike
                        OR LOWER(COALESCE(pub.name, '')) LIKE :qLike
                        OR EXISTS (
                            SELECT 1 FROM book_authors ba
                            JOIN authors a ON a.id = ba.author_id
                            WHERE ba.book_id = b.id AND LOWER(a.name) LIKE :qLike
                        )
                    )
                    """);
            params.addValue("q", normalizedQ).addValue("qLike", "%" + normalizedQ + "%").addValue("qPrefix", normalizedQ + "%");
        }
        if (authorIds != null && !authorIds.isEmpty()) {
            from.append(" AND EXISTS (SELECT 1 FROM book_authors ba WHERE ba.book_id = b.id AND ba.author_id IN (:authorIds))\n");
            params.addValue("authorIds", authorIds);
        }
        if (publisherIds != null && !publisherIds.isEmpty()) {
            from.append(" AND b.publisher_id IN (:publisherIds)\n");
            params.addValue("publisherIds", publisherIds);
        }
        if (normalize(category) != null) {
            from.append(" AND LOWER(b.category_name) = :category\n");
            params.addValue("category", normalize(category));
        }
        if (normalize(genre) != null) {
            from.append(" AND LOWER(b.genre_name) = :genre\n");
            params.addValue("genre", normalize(genre));
        }
        if (minPrice != null) {
            from.append(" AND current_price.amount >= :minPrice\n");
            params.addValue("minPrice", minPrice);
        }
        if (maxPrice != null) {
            from.append(" AND current_price.amount <= :maxPrice\n");
            params.addValue("maxPrice", maxPrice);
        }
        if (Boolean.TRUE.equals(inStock)) from.append(" AND (i.stock - COALESCE(active_reservations.quantity, 0)) > 0\n");
        if (featured != null) {
            from.append(" AND sp.featured = :featured\n");
            params.addValue("featured", featured);
        }

        String order = orderBy(sort, normalizedQ != null);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from, params, Long.class);
        List<Long> ids = jdbc.queryForList("SELECT sp.id " + from + order + " LIMIT :limit OFFSET :offset", params, Long.class);
        return new Result(ids, total == null ? 0 : total);
    }


    public FilterData filters(Long storeId, LocalDate today) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("storeId", storeId).addValue("today", today);
        String commonJoins = """
                FROM store_publications sp
                JOIN inventory i ON i.id = sp.inventory_id
                JOIN books b ON b.id = i.book_id
                JOIN LATERAL (
                    SELECT ip.amount
                    FROM inventory_prices ip
                    WHERE ip.inventory_id = i.id AND ip.effective_from <= :today
                    ORDER BY ip.effective_from DESC, ip.id DESC
                    LIMIT 1
                ) current_price ON TRUE
                """;
        String where = " WHERE sp.store_id = :storeId AND sp.published = TRUE AND i.active = TRUE AND b.active = TRUE ";
        List<IdName> authors = jdbc.query(
                "SELECT DISTINCT a.id, a.name " + commonJoins
                        + " JOIN book_authors ba ON ba.book_id = b.id JOIN authors a ON a.id = ba.author_id "
                        + where + " ORDER BY a.name",
                params, (rs, n) -> new IdName(rs.getLong("id"), rs.getString("name")));
        List<IdName> publishers = jdbc.query(
                "SELECT DISTINCT pub.id, pub.name " + commonJoins
                        + " JOIN publishers pub ON pub.id = b.publisher_id "
                        + where + " ORDER BY pub.name",
                params, (rs, n) -> new IdName(rs.getLong("id"), rs.getString("name")));
        List<String> categories = jdbc.queryForList(
                "SELECT DISTINCT b.category_name " + commonJoins + where
                        + " AND b.category_name IS NOT NULL AND b.category_name <> '' ORDER BY b.category_name",
                params, String.class);
        List<String> genres = jdbc.queryForList(
                "SELECT DISTINCT b.genre_name " + commonJoins + where
                        + " AND b.genre_name IS NOT NULL AND b.genre_name <> '' ORDER BY b.genre_name",
                params, String.class);
        PriceRange range = jdbc.queryForObject(
                "SELECT MIN(current_price.amount) min_price, MAX(current_price.amount) max_price " + commonJoins + where,
                params, (rs, n) -> new PriceRange(rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price")));
        return new FilterData(authors, publishers, categories, genres, range == null ? null : range.min(), range == null ? null : range.max());
    }

    private String orderBy(String sort, boolean hasQuery) {
        String value = sort == null ? "RELEVANCE" : sort.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "PRICE_ASC" -> " ORDER BY current_price.amount ASC, b.title_sort ASC, sp.id ASC";
            case "PRICE_DESC" -> " ORDER BY current_price.amount DESC, b.title_sort ASC, sp.id ASC";
            case "NEWEST" -> " ORDER BY sp.published_at DESC NULLS LAST, sp.id DESC";
            case "OLDEST" -> " ORDER BY sp.published_at ASC NULLS LAST, sp.id ASC";
            case "TITLE_ASC" -> " ORDER BY b.title_sort ASC, sp.id ASC";
            case "TITLE_DESC" -> " ORDER BY b.title_sort DESC, sp.id DESC";
            default -> hasQuery
                    ? " ORDER BY CASE WHEN LOWER(b.title) = :q THEN 0 WHEN LOWER(b.title) LIKE :qPrefix THEN 1 WHEN LOWER(b.title) LIKE :qLike THEN 2 ELSE 3 END, sp.featured DESC, sp.published_at DESC NULLS LAST, sp.id DESC"
                    : " ORDER BY sp.featured DESC, sp.featured_order ASC NULLS LAST, sp.published_at DESC NULLS LAST, sp.id DESC";
        };
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
    public record Result(List<Long> publicationIds, long total) {}
    public record IdName(Long id, String name) {}
    public record PriceRange(BigDecimal min, BigDecimal max) {}
    public record FilterData(List<IdName> authors, List<IdName> publishers, List<String> categories, List<String> genres, BigDecimal minPrice, BigDecimal maxPrice) {}
}
