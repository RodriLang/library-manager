package com.rodrilang.librarymanager.repository.impl;

import com.rodrilang.librarymanager.enums.EditorialPricePresence;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.criteria.BookCatalogCriteria;
import com.rodrilang.librarymanager.repository.BookCatalogQueryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookCatalogQueryRepositoryImpl implements BookCatalogQueryRepository {

    private static final String CURRENT_PRICE_CTE_BODY = """
            current_prices AS (
                SELECT DISTINCT ON (eep.book_id)
                    eep.book_id,
                    eep.price
                FROM effective_editorial_prices eep
                WHERE eep.active = TRUE
                  AND eep.valid_from <= CURRENT_DATE
                ORDER BY
                    eep.book_id,
                    eep.valid_from DESC,
                    eep.id DESC
            )
            """;

    private final EntityManager entityManager;

    @Override
    public Page<Book> findAll(
            BookCatalogCriteria criteria,
            long bookstoreId,
            Pageable pageable
    ) {
        boolean currentPriceRequired =
                criteria.hasPriceFilter()
                        || hasSort(pageable, "editorialPrice");

        String priceCte = currentPriceRequired
                ? currentPriceCte()
                : "";

        String priceJoin = currentPriceRequired
                ? "LEFT JOIN current_prices cp ON cp.book_id = b.id"
                : "";

        String filters = buildFilters(criteria);

        String sql = """
                %s
                SELECT b.*
                FROM books b
                LEFT JOIN publishers p ON p.id = b.publisher_id
                %s
                WHERE %s
                %s
                """.formatted(
                priceCte,
                priceJoin,
                filters,
                buildOrderBy(pageable)
        );

        String countSql = """
                %s
                SELECT COUNT(*)
                FROM books b
                %s
                WHERE %s
                """.formatted(
                priceCte,
                priceJoin,
                filters
        );

        return executePage(
                sql,
                countSql,
                criteria,
                bookstoreId,
                pageable,
                query -> {
                }
        );
    }

    @Override
    public Page<Book> searchByIsbn(
            BookCatalogCriteria criteria,
            String query,
            long bookstoreId,
            Pageable pageable
    ) {
        String priceCte = criteria.hasPriceFilter()
                ? currentPriceCte()
                : "";

        String priceJoin = criteria.hasPriceFilter()
                ? "LEFT JOIN current_prices cp ON cp.book_id = b.id"
                : "";

        String filters = buildFilters(criteria);

        String sql = """
                %s
                SELECT b.*
                FROM books b
                %s
                WHERE %s
                  AND (
                      b.isbn_13 LIKE CONCAT(:query, '%%')
                      OR b.isbn_10 LIKE CONCAT(:query, '%%')
                  )
                ORDER BY
                    CASE
                        WHEN b.isbn_13 = :query THEN 1
                        WHEN b.isbn_10 = :query THEN 1
                        ELSE 2
                    END,
                    b.title_sort ASC,
                    b.id ASC
                """.formatted(
                priceCte,
                priceJoin,
                filters
        );

        String countSql = """
                %s
                SELECT COUNT(*)
                FROM books b
                %s
                WHERE %s
                  AND (
                      b.isbn_13 LIKE CONCAT(:query, '%%')
                      OR b.isbn_10 LIKE CONCAT(:query, '%%')
                  )
                """.formatted(
                priceCte,
                priceJoin,
                filters
        );

        return executePage(
                sql,
                countSql,
                criteria,
                bookstoreId,
                pageable,
                nativeQuery -> nativeQuery.setParameter("query", query)
        );
    }

    @Override
    public Page<Book> searchText(
            BookCatalogCriteria criteria,
            String query,
            String fullTextQuery,
            long bookstoreId,
            Pageable pageable
    ) {
        boolean currentPriceRequired = criteria.hasPriceFilter();

        String currentPrices = currentPriceRequired
                ? CURRENT_PRICE_CTE_BODY + ","
                : "";

        String priceJoin = currentPriceRequired
                ? "LEFT JOIN current_prices cp ON cp.book_id = b.id"
                : "";

        String filters = buildFilters(criteria);

        String sql = """
                WITH
                %s
                visible_books AS NOT MATERIALIZED (
                    SELECT b.*
                    FROM books b
                    %s
                    WHERE %s
                ),
                matches AS (
                    SELECT
                        b.id AS book_id,
                        CASE
                            WHEN b.title_search = :query THEN 1
                            WHEN b.title_search LIKE CONCAT(:query, '%%') THEN 2
                            ELSE 3
                        END AS priority
                    FROM visible_books b
                    WHERE to_tsvector('simple', b.title_search)
                          @@ to_tsquery('simple', :fullTextQuery)
                
                    UNION ALL
                
                    SELECT
                        b.id AS book_id,
                        4 AS priority
                    FROM visible_books b
                    WHERE immutable_unaccent(lower(COALESCE(b.subtitle, '')))
                          LIKE CONCAT(
                              '%%',
                              immutable_unaccent(lower(:query)),
                              '%%'
                          )
                
                    UNION ALL
                
                    SELECT
                        b.id AS book_id,
                        5 AS priority
                    FROM publishers p
                    JOIN visible_books b
                      ON b.publisher_id = p.id
                    WHERE immutable_unaccent(lower(p.name))
                          LIKE CONCAT(
                              '%%',
                              immutable_unaccent(lower(:query)),
                              '%%'
                          )
                
                    UNION ALL
                
                    SELECT
                        ba.book_id,
                        CASE
                            WHEN immutable_unaccent(lower(a.name))
                                 LIKE CONCAT(
                                     immutable_unaccent(lower(:query)),
                                     '%%'
                                 )
                                THEN 3
                            ELSE 4
                        END AS priority
                    FROM authors a
                    JOIN book_authors ba
                      ON ba.author_id = a.id
                    JOIN visible_books b
                      ON b.id = ba.book_id
                    WHERE immutable_unaccent(lower(a.name))
                          LIKE CONCAT(
                              '%%',
                              immutable_unaccent(lower(:query)),
                              '%%'
                          )
                ),
                ranked_matches AS (
                    SELECT
                        book_id,
                        MIN(priority) AS priority
                    FROM matches
                    GROUP BY book_id
                )
                SELECT b.*
                FROM ranked_matches rm
                JOIN visible_books b
                  ON b.id = rm.book_id
                ORDER BY
                    rm.priority ASC,
                    COALESCE(b.title_sort, b.title) ASC,
                    b.id ASC
                """.formatted(
                currentPrices,
                priceJoin,
                filters
        );

        String countSql = """
                WITH
                %s
                visible_books AS NOT MATERIALIZED (
                    SELECT b.*
                    FROM books b
                    %s
                    WHERE %s
                )
                SELECT COUNT(DISTINCT matches.book_id)
                FROM (
                    SELECT b.id AS book_id
                    FROM visible_books b
                    WHERE to_tsvector('simple', b.title_search)
                          @@ to_tsquery('simple', :fullTextQuery)
                
                    UNION ALL
                
                    SELECT b.id AS book_id
                    FROM visible_books b
                    WHERE immutable_unaccent(lower(COALESCE(b.subtitle, '')))
                          LIKE CONCAT(
                              '%%',
                              immutable_unaccent(lower(:query)),
                              '%%'
                          )
                
                    UNION ALL
                
                    SELECT b.id AS book_id
                    FROM publishers p
                    JOIN visible_books b
                      ON b.publisher_id = p.id
                    WHERE immutable_unaccent(lower(p.name))
                          LIKE CONCAT(
                              '%%',
                              immutable_unaccent(lower(:query)),
                              '%%'
                          )
                
                    UNION ALL
                
                    SELECT ba.book_id
                    FROM authors a
                    JOIN book_authors ba
                      ON ba.author_id = a.id
                    JOIN visible_books b
                      ON b.id = ba.book_id
                    WHERE immutable_unaccent(lower(a.name))
                          LIKE CONCAT(
                              '%%',
                              immutable_unaccent(lower(:query)),
                              '%%'
                          )
                ) matches
                """.formatted(
                currentPrices,
                priceJoin,
                filters
        );

        return executePage(
                sql,
                countSql,
                criteria,
                bookstoreId,
                pageable,
                nativeQuery -> {
                    nativeQuery.setParameter("query", query);
                    nativeQuery.setParameter("fullTextQuery", fullTextQuery);
                }
        );
    }

    private String buildFilters(BookCatalogCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                b.active = TRUE
                AND NOT EXISTS (
                    SELECT 1
                    FROM bookstore_excluded_publishers bep
                    WHERE bep.bookstore_id = :bookstoreId
                      AND bep.publisher_id = b.publisher_id
                )
                """);

        if (criteria.publisherId() != null) {
            sql.append("""
                    
                    AND b.publisher_id = :publisherId
                    """);
        }

        if (criteria.authorId() != null) {
            sql.append("""
                    
                    AND EXISTS (
                        SELECT 1
                        FROM book_authors filter_ba
                        WHERE filter_ba.book_id = b.id
                          AND filter_ba.author_id = :authorId
                    )
                    """);
        }

        if (criteria.minPrice() != null) {
            sql.append("""
                    
                    AND cp.price >= :minPrice
                    """);
        }

        if (criteria.maxPrice() != null) {
            sql.append("""
                    
                    AND cp.price <= :maxPrice
                    """);
        }

        if (criteria.priceStatus() == EditorialPricePresence.WITH_PRICE) {
            sql.append("""
                    
                    AND cp.price IS NOT NULL
                    """);
        }

        if (criteria.priceStatus() == EditorialPricePresence.WITHOUT_PRICE) {
            sql.append("""
                    
                    AND cp.price IS NULL
                    """);
        }

        return sql.toString();
    }

    private String currentPriceCte() {
        return """
                WITH %s
                """.formatted(CURRENT_PRICE_CTE_BODY);
    }

    private String buildOrderBy(Pageable pageable) {
        List<String> clauses = new ArrayList<>();

        for (Sort.Order order : pageable.getSort()) {
            String direction = order.isAscending()
                    ? "ASC"
                    : "DESC";

            switch (order.getProperty()) {
                case "title", "titleSort" -> clauses.add("b.title_sort " + direction);

                case "publisher", "publisherName" -> clauses.add("p.name " + direction + " NULLS LAST");

                case "editorialPrice" -> clauses.add("cp.price " + direction + " NULLS LAST");

                default -> {
                }
            }
        }

        if (clauses.isEmpty()) {
            clauses.add("b.title_sort ASC");
        }

        clauses.add("b.id ASC");

        return "ORDER BY " + String.join(", ", clauses);
    }

    private boolean hasSort(
            Pageable pageable,
            String property
    ) {
        return pageable.getSort().stream()
                .anyMatch(order -> property.equals(order.getProperty()));
    }

    private Page<Book> executePage(
            String sql,
            String countSql,
            BookCatalogCriteria criteria,
            long bookstoreId,
            Pageable pageable,
            QueryBinder queryBinder
    ) {
        Query dataQuery = entityManager.createNativeQuery(sql, Book.class);
        Query countQuery = entityManager.createNativeQuery(countSql);

        bindCommonParameters(
                dataQuery,
                criteria,
                bookstoreId
        );

        bindCommonParameters(
                countQuery,
                criteria,
                bookstoreId
        );

        queryBinder.bind(dataQuery);
        queryBinder.bind(countQuery);

        dataQuery.setFirstResult(Math.toIntExact(pageable.getOffset()));
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Book> books = dataQuery.getResultList();

        Number total = (Number) countQuery.getSingleResult();

        return new PageImpl<>(
                books,
                pageable,
                total.longValue()
        );
    }

    private void bindCommonParameters(
            Query query,
            BookCatalogCriteria criteria,
            long bookstoreId
    ) {
        query.setParameter("bookstoreId", bookstoreId);

        if (criteria.publisherId() != null) {
            query.setParameter("publisherId", criteria.publisherId());
        }

        if (criteria.authorId() != null) {
            query.setParameter("authorId", criteria.authorId());
        }

        if (criteria.minPrice() != null) {
            query.setParameter("minPrice", criteria.minPrice());
        }

        if (criteria.maxPrice() != null) {
            query.setParameter("maxPrice", criteria.maxPrice());
        }
    }

    @FunctionalInterface
    private interface QueryBinder {

        void bind(Query query);
    }
}