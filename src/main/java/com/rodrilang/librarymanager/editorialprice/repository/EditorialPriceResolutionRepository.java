package com.rodrilang.librarymanager.editorialprice.repository;

import com.rodrilang.librarymanager.editorialprice.model.EditorialPriceResolution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EditorialPriceResolutionRepository extends JpaRepository<EditorialPriceResolution, Long> {

    boolean existsBySelectedEditorialPriceIdAndActiveTrue(Long editorialPriceId);

    List<EditorialPriceResolution> findByBookIdAndActiveTrueOrderByValidFromDescIdDesc(Long bookId);

    @EntityGraph(attributePaths = {
            "selectedEditorialPrice",
            "selectedEditorialPrice.provider"
    })
    @Query("""
            SELECT resolution
            FROM EditorialPriceResolution resolution
            WHERE resolution.book.id IN :bookIds
              AND resolution.active = true
              AND resolution.validFrom >= :fromDate
            ORDER BY
                resolution.book.id,
                resolution.validFrom,
                resolution.id
            """)
    List<EditorialPriceResolution> findActiveFrom(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("fromDate") LocalDate fromDate
    );

    @EntityGraph(attributePaths = {
            "selectedEditorialPrice",
            "selectedEditorialPrice.provider"
    })
    Optional<EditorialPriceResolution>
    findByBookIdAndValidFromAndActiveTrue(
            Long bookId,
            LocalDate validFrom
    );

    @Query(
            value = """
                    SELECT r
                    FROM EditorialPriceResolution r
                    JOIN FETCH r.book b
                    JOIN FETCH r.selectedEditorialPrice ep
                    LEFT JOIN FETCH ep.provider provider
                    WHERE (:active IS NULL OR r.active = :active)
                    """,
            countQuery = """
                    SELECT COUNT(r)
                    FROM EditorialPriceResolution r
                    WHERE (:active IS NULL OR r.active = :active)
                    """
    )
    Page<EditorialPriceResolution> findAllForList(
            @Param("active") Boolean active,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT r
                    FROM EditorialPriceResolution r
                    JOIN FETCH r.book b
                    JOIN FETCH r.selectedEditorialPrice ep
                    LEFT JOIN FETCH ep.provider provider
                    WHERE (:active IS NULL OR r.active = :active)
                      AND (
                          LOWER(b.title) LIKE CONCAT('%', :query, '%')
                          OR COALESCE(b.isbn13, '') LIKE CONCAT('%', :query, '%')
                          OR COALESCE(b.isbn10, '') LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(ep.sourceName, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(provider.name, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(r.resolvedByUsername) LIKE CONCAT('%', :query, '%')
                      )
                    """,
            countQuery = """
                    SELECT COUNT(r)
                    FROM EditorialPriceResolution r
                    JOIN r.book b
                    JOIN r.selectedEditorialPrice ep
                    LEFT JOIN ep.provider provider
                    WHERE (:active IS NULL OR r.active = :active)
                      AND (
                          LOWER(b.title) LIKE CONCAT('%', :query, '%')
                          OR COALESCE(b.isbn13, '') LIKE CONCAT('%', :query, '%')
                          OR COALESCE(b.isbn10, '') LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(ep.sourceName, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(COALESCE(provider.name, '')) LIKE CONCAT('%', :query, '%')
                          OR LOWER(r.resolvedByUsername) LIKE CONCAT('%', :query, '%')
                      )
                    """
    )
    Page<EditorialPriceResolution> search(
            @Param("active") Boolean active,
            @Param("query") String query,
            Pageable pageable
    );
}