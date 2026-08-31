package com.rodrilang.librarymanager.importer.price.repository;

import com.rodrilang.librarymanager.importer.price.enums.EditorialPriceChange;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportItemOperation;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportItem;
import com.rodrilang.librarymanager.importer.price.repository.projection.PriceListImportPriceChangeCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PriceListImportItemRepository
        extends JpaRepository<PriceListImportItem, Long> {

    boolean existsByJobId(Long jobId);

    @Query("""
            SELECT
                item.job.id AS jobId,
                item.priceChange AS priceChange,
                COUNT(item.id) AS total
            FROM PriceListImportItem item
            WHERE item.job.id IN :jobIds
            GROUP BY
                item.job.id,
                item.priceChange
            """)
    List<PriceListImportPriceChangeCountProjection> countPriceChangesByJobIds(
            @Param("jobIds") Collection<Long> jobIds
    );

    @Query(
            value = """
                    SELECT DISTINCT item.id
                    FROM PriceListImportItem item
                    JOIN item.book book
                    LEFT JOIN book.publisher publisher
                    LEFT JOIN book.authors author
                    WHERE item.job.id = :jobId
                      AND (
                          :priceChange IS NULL
                          OR item.priceChange = :priceChange
                      )
                      AND (
                          :operation IS NULL
                          OR item.operation = :operation
                      )
                      AND (
                          :query = ''
                          OR LOWER(book.title) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(book.isbn13, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(book.isbn10, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(publisher.name, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(author.name, '')) LIKE CONCAT('%', :query, '%')
                      )
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT item.id)
                    FROM PriceListImportItem item
                    JOIN item.book book
                    LEFT JOIN book.publisher publisher
                    LEFT JOIN book.authors author
                    WHERE item.job.id = :jobId
                      AND (
                          :priceChange IS NULL
                          OR item.priceChange = :priceChange
                      )
                      AND (
                          :operation IS NULL
                          OR item.operation = :operation
                      )
                      AND (
                          :query = ''
                          OR LOWER(book.title) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(book.isbn13, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(book.isbn10, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(publisher.name, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(author.name, '')) LIKE CONCAT('%', :query, '%')
                      )
                    """
    )
    Page<Long> findHistoryItemIds(
            @Param("jobId") Long jobId,
            @Param("priceChange") EditorialPriceChange priceChange,
            @Param("operation") PriceListImportItemOperation operation,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT item
            FROM PriceListImportItem item
            JOIN FETCH item.book book
            LEFT JOIN FETCH book.publisher
            LEFT JOIN FETCH book.authors
            JOIN FETCH item.editorialPrice
            WHERE item.id IN :ids
            """)
    List<PriceListImportItem> findHistoryItemsByIds(
            @Param("ids") Collection<Long> ids
    );
}