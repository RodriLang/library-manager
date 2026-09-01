package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceOrigin;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.projection.EditorialPriceImportProjection;
import com.rodrilang.librarymanager.repository.projection.PreviousEditorialPriceProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EditorialPriceRepository extends JpaRepository<EditorialPrice, Long> {

    boolean existsByBookId(Long bookId);

    @Query(value = """
            SELECT ep.*
            FROM editorial_prices ep
            WHERE ep.book_id = :bookId
              AND ep.provider_id = :providerId
              AND ep.active = TRUE
              AND ep.valid_from <= :validFrom
              AND ep.origin IN ('PRICE_LIST', 'MANUAL_DISTRIBUTOR')
            ORDER BY ep.valid_from DESC,
                     CASE ep.origin
                         WHEN 'MANUAL_DISTRIBUTOR' THEN 0
                         ELSE 1
                     END,
                     ep.id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<EditorialPrice> findFirstByBookIdAndProviderIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
            @Param("bookId") Long bookId,
            @Param("providerId") Long providerId,
            @Param("validFrom") LocalDate validFrom
    );

    @Query("""
            SELECT ep
            FROM EditorialPrice ep
            WHERE ep.provider.id = :providerId
              AND ep.book.id IN :bookIds
            ORDER BY ep.validFrom, ep.id
            """)
    List<EditorialPrice> findByProviderIdAndBookIdIn(
            @Param("providerId") Long providerId,
            @Param("bookIds") Collection<Long> bookIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ep
            FROM EditorialPrice ep
            WHERE ep.provider.id = :providerId
              AND ep.book.id IN :bookIds
            ORDER BY ep.validFrom, ep.id
            """)
    List<EditorialPrice> findByProviderIdAndBookIdInForUpdate(
            @Param("providerId") Long providerId,
            @Param("bookIds") Collection<Long> bookIds
    );

    @Query(value = """
            SELECT DISTINCT ON (ep.book_id) ep.*
            FROM editorial_prices ep
            WHERE ep.provider_id = :providerId
              AND ep.book_id IN (:bookIds)
              AND ep.active = TRUE
              AND ep.valid_from <= :date
              AND ep.origin IN ('PRICE_LIST', 'MANUAL_DISTRIBUTOR')
            ORDER BY ep.book_id,
                     ep.valid_from DESC,
                     CASE ep.origin
                         WHEN 'MANUAL_DISTRIBUTOR' THEN 0
                         ELSE 1
                     END,
                     ep.id DESC
            """, nativeQuery = true)
    List<EditorialPrice> findCurrentByProviderAndBookIds(
            @Param("providerId") Long providerId,
            @Param("bookIds") Collection<Long> bookIds,
            @Param("date") LocalDate date
    );

    @Query(value = """
            SELECT DISTINCT ON (ep.book_id, ep.provider_id) ep.*
            FROM editorial_prices ep
            WHERE ep.book_id IN (:bookIds)
              AND ep.provider_id IN (:providerIds)
              AND ep.active = TRUE
              AND ep.valid_from <= :date
              AND ep.origin IN ('PRICE_LIST', 'MANUAL_DISTRIBUTOR')
            ORDER BY ep.book_id,
                     ep.provider_id,
                     ep.valid_from DESC,
                     CASE ep.origin
                         WHEN 'MANUAL_DISTRIBUTOR' THEN 0
                         ELSE 1
                     END,
                     ep.id DESC
            """, nativeQuery = true)
    List<EditorialPrice> findCurrentByBooksAndProviders(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("providerIds") Collection<Long> providerIds,
            @Param("date") LocalDate date
    );

    @Query("""
            SELECT ep.id AS id,
                   ep.book.id AS bookId,
                   ep.price AS price,
                   ep.active AS active
            FROM EditorialPrice ep
            WHERE ep.book.id IN :bookIds
              AND ep.provider.id = :providerId
              AND ep.validFrom = :validFrom
              AND ep.origin = :origin
            """)
    List<EditorialPriceImportProjection> findForImport(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("providerId") Long providerId,
            @Param("validFrom") LocalDate validFrom,
            @Param("origin") EditorialPriceOrigin origin
    );

    @Query(value = """
            SELECT DISTINCT ON (ep.book_id)
                   ep.book_id AS "bookId",
                   ep.price AS "price"
            FROM editorial_prices ep
            WHERE ep.provider_id = :providerId
              AND ep.book_id IN (:bookIds)
              AND ep.origin = 'PRICE_LIST'
              AND ep.active = TRUE
              AND ep.valid_from < :validFrom
            ORDER BY ep.book_id, ep.valid_from DESC, ep.id DESC
            """, nativeQuery = true)
    List<PreviousEditorialPriceProjection> findLatestBeforeImport(
            @Param("providerId") Long providerId,
            @Param("bookIds") Collection<Long> bookIds,
            @Param("validFrom") LocalDate validFrom
    );

    @EntityGraph(attributePaths = "provider")
    @Query("""
            SELECT price
            FROM EditorialPrice price
            WHERE price.book.id IN :bookIds
              AND price.active = true
              AND price.validFrom >= :fromDate
            ORDER BY price.book.id, price.validFrom, price.id
            """)
    List<EditorialPrice> findActiveFrom(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("fromDate") LocalDate fromDate
    );

    @Query("""
            SELECT DISTINCT price.book.id
            FROM EditorialPrice price
            WHERE price.book.id IN :bookIds
              AND price.active = true
              AND price.validFrom < :fromDate
              AND price.origin IN :origins
            """)
    List<Long> findBookIdsWithOfficialPriceBefore(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("origins") Collection<EditorialPriceOrigin> origins
    );

    @EntityGraph(attributePaths = "provider")
    @Query("""
            SELECT price
            FROM EditorialPrice price
            WHERE price.book.id = :bookId
              AND price.validFrom = :validFrom
              AND price.active = true
            ORDER BY price.id
            """)
    List<EditorialPrice> findActiveAt(
            @Param("bookId") Long bookId,
            @Param("validFrom") LocalDate validFrom
    );

    @EntityGraph(attributePaths = "provider")
    List<EditorialPrice> findByBookIdAndActiveTrueOrderByValidFromDescIdDesc(Long bookId);

    Optional<EditorialPrice> findByBookIdAndProviderIdAndValidFromAndOrigin(
            Long bookId,
            Long providerId,
            LocalDate validFrom,
            EditorialPriceOrigin origin
    );
}