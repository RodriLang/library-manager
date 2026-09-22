package com.rodrilang.librarymanager.repository.impl;

import com.rodrilang.librarymanager.dto.internal.InventoryAdvancedFilters;
import com.rodrilang.librarymanager.dto.internal.InventoryFilterOption;
import com.rodrilang.librarymanager.dto.internal.InventoryStockSummaryCounts;
import com.rodrilang.librarymanager.enums.InventoryStockFilter;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryQueryRepository;
import com.rodrilang.librarymanager.repository.criteria.InventorySearchCriteria;
import com.rodrilang.librarymanager.util.TextNormalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class InventoryQueryRepositoryImpl implements InventoryQueryRepository {

    private final EntityManager entityManager;

    @Override
    public Page<Inventory> find(
            Long bookstoreId,
            InventorySearchCriteria criteria,
            Pageable pageable
    ) {
        String query = criteria.normalizedQuery();

        if (query.isBlank()) {
            return findPage(
                    bookstoreId,
                    criteria,
                    pageable
            );
        }

        boolean identifierQuery =
                query.matches("[0-9Xx\\-\\s]+");

        if (!criteria.force()) {
            int minimumLength =
                    identifierQuery ? 8 : 3;

            if (query.length() < minimumLength) {
                return Page.empty(pageable);
            }
        }

        if (identifierQuery) {
            return searchByIsbn(
                    bookstoreId,
                    query,
                    criteria,
                    pageable
            );
        }

        return searchText(
                bookstoreId,
                query,
                criteria,
                pageable
        );
    }

    @Override
    public InventoryStockSummaryCounts summarize(
            Long bookstoreId,
            InventoryAdvancedFilters filters
    ) {
        InventoryAdvancedFilters resolvedFilters =
                filters != null
                        ? filters
                        : InventoryAdvancedFilters.empty();

        StringBuilder sql =
                new StringBuilder("""
                        SELECT
                            COUNT(*) AS total,
                        
                            COUNT(*) FILTER (
                                WHERE i.stock > i.minimum_stock
                            ) AS available,
                        
                            COUNT(*) FILTER (
                                WHERE i.stock > 0
                                  AND i.stock <= i.minimum_stock
                            ) AS low_stock,
                        
                            COUNT(*) FILTER (
                                WHERE i.stock = 0
                            ) AS out_of_stock
                        
                        FROM inventory i
                        JOIN books b
                          ON b.id = i.book_id
                        
                        WHERE i.bookstore_id = :bookstoreId
                          AND b.active = TRUE
                        """);

        appendAdvancedFilters(
                sql,
                resolvedFilters
        );

        Query query =
                entityManager.createNativeQuery(
                        sql.toString()
                );

        bindAdvancedFilters(
                query,
                bookstoreId,
                resolvedFilters
        );

        Object[] row =
                (Object[]) query.getSingleResult();

        return new InventoryStockSummaryCounts(
                number(row[0]),
                number(row[1]),
                number(row[2]),
                number(row[3])
        );
    }

    @Override
    public Page<InventoryFilterOption> searchFilterAuthors(
            Long bookstoreId,
            String value,
            Pageable pageable
    ) {
        String query = normalizeSearchQuery(value);
        String tokenQuery = TextNormalizer.normalizeForTokenPrefixSearch(value);

        if (query == null || query.isBlank() || tokenQuery.isBlank()) {
            return Page.empty(pageable);
        }

        String sql = """
                SELECT DISTINCT
                    a.id,
                    a.name,
                    a.name_normalized
                
                FROM inventory i
                JOIN books b
                  ON b.id = i.book_id
                JOIN book_authors ba
                  ON ba.book_id = b.id
                JOIN authors a
                  ON a.id = ba.author_id
                
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                  AND b.active = TRUE
                  AND to_tsvector('simple', a.name_normalized)
                      @@ to_tsquery('simple', :tokenQuery)
                
                ORDER BY
                    a.name_normalized ASC,
                    a.id ASC
                """;

        String countSql = """
                SELECT COUNT(DISTINCT a.id)
                
                FROM inventory i
                JOIN books b
                  ON b.id = i.book_id
                JOIN book_authors ba
                  ON ba.book_id = b.id
                JOIN authors a
                  ON a.id = ba.author_id
                
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                  AND b.active = TRUE
                  AND to_tsvector('simple', a.name_normalized)
                      @@ to_tsquery('simple', :tokenQuery)
                """;

        Query dataQuery =
                entityManager.createNativeQuery(sql);

        dataQuery.setParameter(
                "bookstoreId",
                bookstoreId
        );

        dataQuery.setParameter(
                "tokenQuery",
                tokenQuery
        );

        if (pageable.isPaged()) {
            dataQuery.setFirstResult(
                    Math.toIntExact(pageable.getOffset())
            );

            dataQuery.setMaxResults(
                    pageable.getPageSize()
            );
        }

        List<InventoryFilterOption> content =
                dataQuery
                        .getResultList()
                        .stream()
                        .map(this::mapFilterOption)
                        .toList();

        Query totalQuery =
                entityManager.createNativeQuery(countSql);

        totalQuery.setParameter(
                "bookstoreId",
                bookstoreId
        );

        totalQuery.setParameter(
                "tokenQuery",
                tokenQuery
        );

        long total =
                ((Number) totalQuery.getSingleResult())
                        .longValue();

        return new PageImpl<>(
                content,
                pageable,
                total
        );
    }

    @Override
    public Page<InventoryFilterOption> searchFilterPublishers(
            Long bookstoreId,
            String value,
            Pageable pageable
    ) {
        String query = normalizeSearchQuery(value);
        String tokenQuery = TextNormalizer.normalizeForTokenPrefixSearch(value);

        if (query == null || query.isBlank() || tokenQuery.isBlank()) {
            return Page.empty(pageable);
        }

        String sql = """
                SELECT DISTINCT
                    p.id,
                    p.name,
                    p.name_normalized
                
                FROM inventory i
                JOIN books b
                  ON b.id = i.book_id
                JOIN publishers p
                  ON p.id = b.publisher_id
                
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                  AND b.active = TRUE
                  AND to_tsvector('simple', p.name_normalized)
                      @@ to_tsquery('simple', :tokenQuery)
                
                ORDER BY
                    p.name_normalized ASC,
                    p.id ASC
                """;

        String countSql = """
                SELECT COUNT(DISTINCT p.id)
                
                FROM inventory i
                JOIN books b
                  ON b.id = i.book_id
                JOIN publishers p
                  ON p.id = b.publisher_id
                
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                  AND b.active = TRUE
                  AND to_tsvector('simple', p.name_normalized)
                      @@ to_tsquery('simple', :tokenQuery)
                """;

        Query dataQuery =
                entityManager.createNativeQuery(sql);

        dataQuery.setParameter(
                "bookstoreId",
                bookstoreId
        );

        dataQuery.setParameter(
                "tokenQuery",
                tokenQuery
        );

        if (pageable.isPaged()) {
            dataQuery.setFirstResult(
                    Math.toIntExact(pageable.getOffset())
            );

            dataQuery.setMaxResults(
                    pageable.getPageSize()
            );
        }

        List<InventoryFilterOption> content =
                dataQuery
                        .getResultList()
                        .stream()
                        .map(this::mapFilterOption)
                        .toList();

        Query totalQuery =
                entityManager.createNativeQuery(countSql);

        totalQuery.setParameter(
                "bookstoreId",
                bookstoreId
        );

        totalQuery.setParameter(
                "tokenQuery",
                tokenQuery
        );

        long total =
                ((Number) totalQuery.getSingleResult())
                        .longValue();

        return new PageImpl<>(
                content,
                pageable,
                total
        );
    }

    @Override
    public List<InventoryFilterOption> findFilterAuthorsByIds(
            Long bookstoreId,
            Collection<Long> authorIds
    ) {
        if (authorIds == null || authorIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT DISTINCT
                    a.id,
                    a.name,
                    a.name_normalized
                
                FROM inventory i
                JOIN books b
                  ON b.id = i.book_id
                JOIN book_authors ba
                  ON ba.book_id = b.id
                JOIN authors a
                  ON a.id = ba.author_id
                
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                  AND b.active = TRUE
                  AND a.id IN (:authorIds)
                
                ORDER BY
                    a.name_normalized ASC,
                    a.id ASC
                """;

        Query query =
                entityManager.createNativeQuery(sql);

        query.setParameter(
                "bookstoreId",
                bookstoreId
        );

        bindIdList(
                query,
                "authorIds",
                authorIds
        );

        return query
                .getResultList()
                .stream()
                .map(this::mapFilterOption)
                .toList();
    }

    @Override
    public List<InventoryFilterOption> findFilterPublishersByIds(
            Long bookstoreId,
            Collection<Long> publisherIds
    ) {
        if (publisherIds == null || publisherIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT DISTINCT
                    p.id,
                    p.name,
                    p.name_normalized
                
                FROM inventory i
                JOIN books b
                  ON b.id = i.book_id
                JOIN publishers p
                  ON p.id = b.publisher_id
                
                WHERE i.bookstore_id = :bookstoreId
                  AND i.active = TRUE
                  AND b.active = TRUE
                  AND p.id IN (:publisherIds)
                
                ORDER BY
                    p.name_normalized ASC,
                    p.id ASC
                """;

        Query query =
                entityManager.createNativeQuery(sql);

        query.setParameter(
                "bookstoreId",
                bookstoreId
        );

        bindIdList(
                query,
                "publisherIds",
                publisherIds
        );

        return query
                .getResultList()
                .stream()
                .map(this::mapFilterOption)
                .toList();
    }

    @Override
    public List<Long> findIds(
            Long bookstoreId,
            InventorySearchCriteria criteria,
            Collection<Long> excludedInventoryIds
    ) {
        String query = criteria.normalizedQuery();

        if (query.isBlank()) {
            return findIdsWithoutSearch(
                    bookstoreId,
                    criteria,
                    excludedInventoryIds
            );
        }

        boolean identifierQuery =
                query.matches("[0-9Xx\\-\\s]+");

        if (!criteria.force()) {
            int minimumLength =
                    identifierQuery ? 8 : 3;

            if (query.length() < minimumLength) {
                return List.of();
            }
        }

        if (identifierQuery) {
            return findIdsByIsbn(
                    bookstoreId,
                    query,
                    criteria,
                    excludedInventoryIds
            );
        }

        return findIdsByText(
                bookstoreId,
                query,
                criteria,
                excludedInventoryIds
        );
    }

    private Page<Inventory> findPage(
            Long bookstoreId,
            InventorySearchCriteria criteria,
            Pageable pageable
    ) {
        StringBuilder dataSql =
                baseInventoryQuery(
                        "SELECT i.id",
                        criteria
                );

        dataSql
                .append("\nORDER BY ")
                .append(resolveOrderBy(pageable));

        StringBuilder countSql =
                baseInventoryQuery(
                        "SELECT COUNT(*)",
                        criteria
                );

        return executePage(
                dataSql.toString(),
                countSql.toString(),
                bookstoreId,
                criteria,
                pageable,
                query -> {
                }
        );
    }

    private Page<Inventory> searchByIsbn(
            Long bookstoreId,
            String value,
            InventorySearchCriteria criteria,
            Pageable pageable
    ) {
        String normalizedIdentifier =
                normalizeSearchIdentifier(value);

        if (normalizedIdentifier == null) {
            return Page.empty(pageable);
        }

        StringBuilder dataSql =
                baseInventoryQuery(
                        "SELECT i.id",
                        criteria
                );

        appendIsbnPredicate(dataSql);

        dataSql.append("""
                
                ORDER BY
                    CASE
                        WHEN b.isbn_13 = :query
                          OR b.isbn_10 = :query
                            THEN 1
                        ELSE 2
                    END,
                    COALESCE(b.title_sort, b.title) ASC,
                    i.id ASC
                """);

        StringBuilder countSql =
                baseInventoryQuery(
                        "SELECT COUNT(*)",
                        criteria
                );

        appendIsbnPredicate(countSql);

        return executePage(
                dataSql.toString(),
                countSql.toString(),
                bookstoreId,
                criteria,
                pageable,
                query ->
                        query.setParameter(
                                "query",
                                normalizedIdentifier
                        )
        );
    }

    private Page<Inventory> searchText(
            Long bookstoreId,
            String value,
            InventorySearchCriteria criteria,
            Pageable pageable
    ) {
        String searchQuery =
                TextNormalizer.normalizeForSearch(value);

        String fullTextQuery =
                TextNormalizer.normalizeForFullTextSearch(value);

        String entityTokenQuery =
                TextNormalizer.normalizeForTokenPrefixSearch(value);

        if (
                searchQuery == null
                        || searchQuery.isBlank()
                        || fullTextQuery == null
                        || fullTextQuery.isBlank()
                        || entityTokenQuery.isBlank()
        ) {
            return Page.empty(pageable);
        }

        String cte =
                buildTextSearchCte(criteria);

        String dataSql =
                cte
                        + """
                        
                        SELECT rm.inventory_id
                        FROM ranked_matches rm
                        JOIN inventory i
                          ON i.id = rm.inventory_id
                        JOIN books b
                          ON b.id = i.book_id
                        
                        ORDER BY
                            rm.priority ASC,
                            COALESCE(b.title_sort, b.title) ASC,
                            i.id ASC
                        """;

        String countSql =
                cte
                        + """
                        
                        SELECT COUNT(*)
                        FROM ranked_matches
                        """;

        return executePage(
                dataSql,
                countSql,
                bookstoreId,
                criteria,
                pageable,
                query -> {
                    query.setParameter(
                            "query",
                            searchQuery
                    );

                    query.setParameter(
                            "fullTextQuery",
                            fullTextQuery
                    );

                    query.setParameter(
                            "entityTokenQuery",
                            entityTokenQuery
                    );
                }
        );
    }

    private StringBuilder baseInventoryQuery(
            String select,
            InventorySearchCriteria criteria
    ) {
        StringBuilder sql =
                new StringBuilder(select)
                        .append("""
                                
                                FROM inventory i
                                JOIN books b
                                  ON b.id = i.book_id
                                
                                WHERE i.bookstore_id = :bookstoreId
                                  AND i.active = TRUE
                                  AND b.active = TRUE
                                """);

        appendStockFilter(
                sql,
                criteria.resolvedStock()
        );

        appendAdvancedFilters(
                sql,
                criteria.resolvedFilters()
        );

        return sql;
    }

    private String buildTextSearchCte(
            InventorySearchCriteria criteria
    ) {
        return buildTextSearchCte(
                criteria,
                List.of()
        );
    }

    private String buildTextSearchCte(
            InventorySearchCriteria criteria,
            Collection<Long> excludedInventoryIds
    ) {
        StringBuilder sql =
                new StringBuilder("""
                        WITH filtered_inventory AS (
                            SELECT
                                i.id,
                                i.book_id
                        
                            FROM inventory i
                            JOIN books b
                              ON b.id = i.book_id
                        
                            WHERE i.bookstore_id = :bookstoreId
                              AND b.active = TRUE
                        """);

        appendStockFilter(
                sql,
                criteria.resolvedStock()
        );

        appendAdvancedFilters(
                sql,
                criteria.resolvedFilters()
        );

        appendExcludedInventoryIds(
                sql,
                excludedInventoryIds
        );

        sql.append("""
                ),
                
                matches AS (
                    SELECT
                        fi.id AS inventory_id,
                        CASE
                            WHEN b.title_search = :query
                                THEN 1
                            WHEN b.title_search LIKE concat(:query, '%')
                                THEN 2
                            ELSE 3
                        END AS priority
                
                    FROM filtered_inventory fi
                    JOIN books b
                      ON b.id = fi.book_id
                
                    WHERE to_tsvector(
                              'simple',
                              b.title_search
                          )
                          @@ to_tsquery(
                              'simple',
                              :fullTextQuery
                          )
                
                    UNION ALL
                
                    SELECT
                        fi.id AS inventory_id,
                        4 AS priority
                
                    FROM filtered_inventory fi
                    JOIN books b
                      ON b.id = fi.book_id
                
                    WHERE immutable_unaccent(
                              lower(
                                  coalesce(
                                      b.subtitle,
                                      ''
                                  )
                              )
                          )
                          LIKE concat(
                              '%',
                              immutable_unaccent(
                                  lower(:query)
                              ),
                              '%'
                          )
                
                    UNION ALL
                
                    SELECT
                        fi.id AS inventory_id,
                        5 AS priority
                
                    FROM filtered_inventory fi
                    JOIN books b
                      ON b.id = fi.book_id
                    JOIN publishers p
                      ON p.id = b.publisher_id
                
                    WHERE to_tsvector(
                              'simple',
                              p.name_normalized
                          )
                          @@ to_tsquery(
                              'simple',
                              :entityTokenQuery
                          )
                
                    UNION ALL
                
                    SELECT
                        fi.id AS inventory_id,
                        CASE
                            WHEN a.name_normalized = :query THEN 3
                            WHEN a.name_normalized LIKE concat(:query, '%') THEN 3
                            ELSE 4
                        END AS priority
                
                    FROM filtered_inventory fi
                    JOIN book_authors ba
                      ON ba.book_id = fi.book_id
                    JOIN authors a
                      ON a.id = ba.author_id
                
                    WHERE to_tsvector(
                              'simple',
                              a.name_normalized
                          )
                          @@ to_tsquery(
                              'simple',
                              :entityTokenQuery
                          )
                ),
                
                ranked_matches AS (
                    SELECT
                        inventory_id,
                        MIN(priority) AS priority
                
                    FROM matches
                
                    GROUP BY inventory_id
                )
                """);

        return sql.toString();
    }

    private void appendExcludedInventoryIds(
            StringBuilder sql,
            Collection<Long> excludedInventoryIds
    ) {
        if (
                excludedInventoryIds == null
                        || excludedInventoryIds.isEmpty()
        ) {
            return;
        }

        sql.append("""
                
                AND i.id NOT IN (:excludedInventoryIds)
                """);
    }

    private void bindExcludedInventoryIds(
            Query query,
            Collection<Long> excludedInventoryIds
    ) {
        if (
                excludedInventoryIds == null
                        || excludedInventoryIds.isEmpty()
        ) {
            return;
        }

        bindIdList(
                query,
                "excludedInventoryIds",
                excludedInventoryIds
        );
    }

    private List<Long> extractIds(
            Query query
    ) {
        return query
                .getResultList()
                .stream()
                .map(result ->
                        ((Number) result).longValue()
                )
                .toList();
    }

    private void appendIsbnPredicate(
            StringBuilder sql
    ) {
        sql.append("""
                
                AND (
                    b.isbn_13 LIKE concat(:query, '%')
                    OR b.isbn_10 LIKE concat(:query, '%')
                )
                """);
    }

    private void appendStockFilter(
            StringBuilder sql,
            InventoryStockFilter stock
    ) {
        switch (stock) {
            case AVAILABLE -> sql.append("""
                    
                    AND i.stock > i.minimum_stock
                    """);

            case LOW -> sql.append("""
                    
                    AND i.stock > 0
                    AND i.stock <= i.minimum_stock
                    """);

            case OUT -> sql.append("""
                    
                    AND i.stock = 0
                    """);

            case ALL -> {
            }
        }
    }

    private void appendAdvancedFilters(
            StringBuilder sql,
            InventoryAdvancedFilters filters
    ) {
        switch (filters.resolvedActive()) {
            case ACTIVE -> sql.append("""
                    
                    AND i.active = TRUE
                    """);

            case INACTIVE -> sql.append("""
                    
                    AND i.active = FALSE
                    """);

            case ALL -> {
            }
        }

        if (filters.condition() != null) {
            sql.append("""
                    
                    AND i.condition = :condition
                    """);
        }

        if (!filters.publisherIds().isEmpty()) {
            sql.append("""
                    
                    AND b.publisher_id IN (:publisherIds)
                    """);
        }

        if (!filters.authorIds().isEmpty()) {
            sql.append("""
                    
                    AND EXISTS (
                        SELECT 1
                        FROM book_authors ba_filter
                        WHERE ba_filter.book_id = b.id
                          AND ba_filter.author_id IN (:authorIds)
                    )
                    """);
        }

        switch (filters.resolvedPriceMode()) {
            case EDITORIAL -> sql.append("""
                    
                    AND i.editorial_price_sync_enabled = TRUE
                    """);

            case INDEPENDENT -> sql.append("""
                    
                    AND i.editorial_price_sync_enabled = FALSE
                    """);

            case ALL -> {
            }
        }
    }

    private Page<Inventory> executePage(
            String dataSql,
            String countSql,
            Long bookstoreId,
            InventorySearchCriteria criteria,
            Pageable pageable,
            Consumer<Query> additionalBinder
    ) {
        Query dataQuery = entityManager.createNativeQuery(dataSql);

        bindSearchFilters(
                dataQuery,
                bookstoreId,
                criteria
        );

        additionalBinder.accept(dataQuery);

        if (pageable.isPaged()) {
            dataQuery.setFirstResult(Math.toIntExact(pageable.getOffset()));

            dataQuery.setMaxResults(pageable.getPageSize());
        }

        List<?> results = dataQuery.getResultList();

        List<Long> ids = results.stream()
                .map(result -> ((Number) result).longValue())
                .toList();

        Query countQuery =
                entityManager.createNativeQuery(countSql);

        bindSearchFilters(
                countQuery,
                bookstoreId,
                criteria
        );

        additionalBinder.accept(countQuery);

        long total =
                ((Number) countQuery.getSingleResult())
                        .longValue();

        List<Inventory> content =
                fetchInventories(
                        bookstoreId,
                        ids
                );

        return new PageImpl<>(
                content,
                pageable,
                total
        );
    }

    private void bindSearchFilters(
            Query query,
            Long bookstoreId,
            InventorySearchCriteria criteria
    ) {
        bindAdvancedFilters(
                query,
                bookstoreId,
                criteria.resolvedFilters()
        );
    }

    private void bindAdvancedFilters(
            Query query,
            Long bookstoreId,
            InventoryAdvancedFilters filters
    ) {
        query.setParameter(
                "bookstoreId",
                bookstoreId
        );

        if (filters.condition() != null) {
            query.setParameter(
                    "condition",
                    filters.condition().name()
            );
        }

        if (!filters.publisherIds().isEmpty()) {
            bindIdList(
                    query,
                    "publisherIds",
                    filters.publisherIds()
            );
        }

        if (!filters.authorIds().isEmpty()) {
            bindIdList(
                    query,
                    "authorIds",
                    filters.authorIds()
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

    private List<Inventory> fetchInventories(
            Long bookstoreId,
            List<Long> ids
    ) {
        if (ids.isEmpty()) {
            return List.of();
        }

        List<Inventory> inventories =
                entityManager
                        .createQuery(
                                """
                                        SELECT DISTINCT i
                                        FROM Inventory i
                                        JOIN FETCH i.book b
                                        LEFT JOIN FETCH b.publisher
                                        LEFT JOIN FETCH b.authors
                                        WHERE i.bookstore.id = :bookstoreId
                                          AND i.id IN :ids
                                        """,
                                Inventory.class
                        )
                        .setParameter(
                                "bookstoreId",
                                bookstoreId
                        )
                        .setParameter(
                                "ids",
                                ids
                        )
                        .getResultList();

        Map<Long, Inventory> byId =
                inventories
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Inventory::getId,
                                        Function.identity()
                                )
                        );

        return ids
                .stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private String resolveOrderBy(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return """
                    i.created_at DESC,
                    i.id DESC
                    """;
        }

        List<String> orders = new ArrayList<>();

        for (Sort.Order order : pageable.getSort()) {
            String column = resolveSortColumn(order.getProperty());

            if (column == null) {
                continue;
            }

            orders.add(
                    column
                            + (order.isAscending()
                            ? " ASC"
                            : " DESC")
            );
        }

        if (orders.isEmpty()) {
            return """
                    i.created_at DESC,
                    i.id DESC
                    """;
        }

        Sort.Order primaryOrder = pageable.getSort().stream()
                .findFirst()
                .orElse(null);

        if (primaryOrder != null && "createdAt".equals(primaryOrder.getProperty())) {
            orders.add(primaryOrder.isAscending() ? "i.id ASC" : "i.id DESC");
        } else {
            orders.add("i.id ASC");
        }

        return String.join(", ", orders);
    }

    private String resolveSortColumn(String property) {
        return switch (property) {
            case "title",
                 "book.titleSort" -> "COALESCE(b.title_sort, b.title)";

            case "createdAt" -> "i.created_at";

            case "salePrice" -> "i.sale_price";

            case "stock" -> "i.stock";

            default -> null;
        };
    }

    private List<Long> findIdsWithoutSearch(
            Long bookstoreId,
            InventorySearchCriteria criteria,
            Collection<Long> excludedInventoryIds
    ) {
        StringBuilder sql =
                baseInventoryQuery(
                        "SELECT i.id",
                        criteria
                );

        appendExcludedInventoryIds(
                sql,
                excludedInventoryIds
        );

        sql.append("""
                
                ORDER BY i.id ASC
                """);

        Query query =
                entityManager.createNativeQuery(
                        sql.toString()
                );

        bindSearchFilters(
                query,
                bookstoreId,
                criteria
        );

        bindExcludedInventoryIds(
                query,
                excludedInventoryIds
        );

        return extractIds(query);
    }

    private List<Long> findIdsByIsbn(
            Long bookstoreId,
            String value,
            InventorySearchCriteria criteria,
            Collection<Long> excludedInventoryIds
    ) {
        String normalizedIdentifier =
                normalizeSearchIdentifier(value);

        if (normalizedIdentifier == null) {
            return List.of();
        }

        StringBuilder sql =
                baseInventoryQuery(
                        "SELECT i.id",
                        criteria
                );

        appendIsbnPredicate(sql);

        appendExcludedInventoryIds(
                sql,
                excludedInventoryIds
        );

        sql.append("""
                
                ORDER BY i.id ASC
                """);

        Query query =
                entityManager.createNativeQuery(
                        sql.toString()
                );

        bindSearchFilters(
                query,
                bookstoreId,
                criteria
        );

        query.setParameter(
                "query",
                normalizedIdentifier
        );

        bindExcludedInventoryIds(
                query,
                excludedInventoryIds
        );

        return extractIds(query);
    }

    private List<Long> findIdsByText(
            Long bookstoreId,
            String value,
            InventorySearchCriteria criteria,
            Collection<Long> excludedInventoryIds
    ) {
        String searchQuery =
                TextNormalizer.normalizeForSearch(value);

        String fullTextQuery =
                TextNormalizer.normalizeForFullTextSearch(value);

        String entityTokenQuery =
                TextNormalizer.normalizeForTokenPrefixSearch(value);

        if (
                searchQuery == null
                        || searchQuery.isBlank()
                        || fullTextQuery == null
                        || fullTextQuery.isBlank()
                        || entityTokenQuery.isBlank()
        ) {
            return List.of();
        }

        String sql =
                buildTextSearchCte(
                        criteria,
                        excludedInventoryIds
                )
                        + """
                        
                        SELECT rm.inventory_id
                        FROM ranked_matches rm
                        ORDER BY rm.inventory_id ASC
                        """;

        Query query =
                entityManager.createNativeQuery(sql);

        bindSearchFilters(
                query,
                bookstoreId,
                criteria
        );

        bindExcludedInventoryIds(
                query,
                excludedInventoryIds
        );

        query.setParameter(
                "query",
                searchQuery
        );

        query.setParameter(
                "fullTextQuery",
                fullTextQuery
        );

        query.setParameter(
                "entityTokenQuery",
                entityTokenQuery
        );

        return extractIds(query);
    }

    private String normalizeSearchIdentifier(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value
                        .trim()
                        .toUpperCase()
                        .replaceAll(
                                "[^0-9X]",
                                ""
                        );

        return normalized.isBlank()
                ? null
                : normalized;
    }

    private long number(
            Object value
    ) {
        return value != null
                ? ((Number) value).longValue()
                : 0L;
    }

    private InventoryFilterOption mapFilterOption(
            Object result
    ) {
        Object[] row = (Object[]) result;

        return new InventoryFilterOption(
                ((Number) row[0]).longValue(),
                (String) row[1]
        );
    }

    private String normalizeSearchQuery(String value) {
        String normalized = TextNormalizer.normalizeForMatch(value);

        if (normalized == null || normalized.length() < 2) {
            return null;
        }

        return normalized;
    }
}