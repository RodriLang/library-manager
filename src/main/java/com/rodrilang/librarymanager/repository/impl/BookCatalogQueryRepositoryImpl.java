package com.rodrilang.librarymanager.repository.impl;

import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.criteria.BookCatalogCriteria;
import com.rodrilang.librarymanager.repository.BookCatalogQueryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookCatalogQueryRepositoryImpl implements BookCatalogQueryRepository {

    private final EntityManager entityManager;

    @Override
    public Page<Book> findAll(
            BookCatalogCriteria criteria,
            long bookstoreId,
            Pageable pageable
    ) {
        String priceCte = "";
        String priceJoin = "";

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
        String priceCte = "";
        String priceJoin = "";

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
            String entityTokenQuery,
            long bookstoreId,
            Pageable pageable
    ) {
        String currentPrices = "";
        String priceJoin = "";

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
                    WHERE to_tsvector('simple', p.name_normalized)
                          @@ to_tsquery('simple', :entityTokenQuery)
                
                    UNION ALL
                
                    SELECT
                        ba.book_id,
                        CASE
                            WHEN a.name_normalized = :query THEN 3
                            WHEN a.name_normalized LIKE CONCAT(:query, '%%') THEN 3
                            ELSE 4
                        END AS priority
                    FROM authors a
                    JOIN book_authors ba
                      ON ba.author_id = a.id
                    JOIN visible_books b
                      ON b.id = ba.book_id
                    WHERE to_tsvector('simple', a.name_normalized)
                          @@ to_tsquery('simple', :entityTokenQuery)
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
                    WHERE to_tsvector('simple', p.name_normalized)
                          @@ to_tsquery('simple', :entityTokenQuery)
                
                    UNION ALL
                
                    SELECT ba.book_id
                    FROM authors a
                    JOIN book_authors ba
                      ON ba.author_id = a.id
                    JOIN visible_books b
                      ON b.id = ba.book_id
                    WHERE to_tsvector('simple', a.name_normalized)
                          @@ to_tsquery('simple', :entityTokenQuery)
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
                    nativeQuery.setParameter("entityTokenQuery", entityTokenQuery);
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

        if (!criteria.publisherIds().isEmpty()) {
            sql.append("""
                    
                    AND b.publisher_id IN (:publisherIds)
                    """);
        }

        if (!criteria.authorIds().isEmpty()) {
            sql.append("""
                    
                    AND EXISTS (
                        SELECT 1
                        FROM book_authors filter_ba
                        WHERE filter_ba.book_id = b.id
                          AND filter_ba.author_id IN (:authorIds)
                    )
                    """);
        }

        return sql.toString();
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

        if (!criteria.publisherIds().isEmpty()) {
            bindIdList(
                    query,
                    "publisherIds",
                    criteria.publisherIds()
            );
        }

        if (!criteria.authorIds().isEmpty()) {
            bindIdList(
                    query,
                    "authorIds",
                    criteria.authorIds()
            );
        }

    }

    private void bindIdList(
            Query query,
            String parameterName,
            Collection<Long> values
    ) {
        query.unwrap(NativeQuery.class)
                .setParameterList(
                        parameterName,
                        values
                );
    }

    @FunctionalInterface
    private interface QueryBinder {

        void bind(Query query);
    }
}