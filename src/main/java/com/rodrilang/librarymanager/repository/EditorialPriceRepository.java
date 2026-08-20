package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.projection.EditorialPriceImportProjection;
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

public interface EditorialPriceRepository
        extends JpaRepository<EditorialPrice, Long> {

    boolean existsByBookId(Long bookId);

    @EntityGraph(attributePaths = "provider")
    Optional<EditorialPrice>
    findFirstByBookIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDescIdDesc(
            Long bookId,
            LocalDate validFrom
    );

    Optional<EditorialPrice>
    findFirstByBookIdAndProviderIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
            Long bookId,
            Long providerId,
            LocalDate validFrom
    );

    @Query("""
            SELECT ep
            FROM EditorialPrice ep
            WHERE ep.provider.id = :providerId
              AND ep.book.id IN :bookIds
            ORDER BY ep.validFrom
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
            ORDER BY ep.validFrom
            """)
    List<EditorialPrice> findByProviderIdAndBookIdInForUpdate(
            @Param("providerId") Long providerId,
            @Param("bookIds") Collection<Long> bookIds
    );

    @Query("""
            SELECT ep
            FROM EditorialPrice ep
            WHERE ep.provider.id = :providerId
              AND ep.book.id IN :bookIds
              AND ep.active = true
              AND ep.validFrom <= :date
              AND ep.validFrom = (
                  SELECT MAX(ep2.validFrom)
                  FROM EditorialPrice ep2
                  WHERE ep2.provider.id = ep.provider.id
                    AND ep2.book.id = ep.book.id
                    AND ep2.active = true
                    AND ep2.validFrom <= :date
              )
            """)
    List<EditorialPrice> findCurrentByProviderAndBookIds(
            @Param("providerId") Long providerId,
            @Param("bookIds") Collection<Long> bookIds,
            @Param("date") LocalDate date
    );

    @Query("""
            SELECT ep
            FROM EditorialPrice ep
            WHERE ep.book.id IN :bookIds
              AND ep.provider.id IN :providerIds
              AND ep.active = true
              AND ep.validFrom <= :date
              AND ep.validFrom = (
                  SELECT MAX(ep2.validFrom)
                  FROM EditorialPrice ep2
                  WHERE ep2.book.id = ep.book.id
                    AND ep2.provider.id = ep.provider.id
                    AND ep2.active = true
                    AND ep2.validFrom <= :date
              )
            """)
    List<EditorialPrice> findCurrentByBooksAndProviders(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("providerIds") Collection<Long> providerIds,
            @Param("date") LocalDate date
    );

    @Query("""
            SELECT ep
            FROM EditorialPrice ep
            WHERE ep.book.id IN :bookIds
              AND ep.active = true
              AND ep.validFrom <= :date
              AND NOT EXISTS (
                  SELECT ep2.id
                  FROM EditorialPrice ep2
                  WHERE ep2.book.id = ep.book.id
                    AND ep2.active = true
                    AND ep2.validFrom <= :date
                    AND (
                        ep2.validFrom > ep.validFrom
                        OR (
                            ep2.validFrom = ep.validFrom
                            AND ep2.id > ep.id
                        )
                    )
              )
            """)
    List<EditorialPrice> findCurrentByBookIds(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("date") LocalDate date
    );

    @Query("""
            SELECT
                ep.id AS id,
                ep.book.id AS bookId,
                ep.price AS price,
                ep.active AS active
            FROM EditorialPrice ep
            WHERE ep.book.id IN :bookIds
              AND ep.provider.id = :providerId
              AND ep.validFrom = :validFrom
            """)
    List<EditorialPriceImportProjection> findForImport(
            Collection<Long> bookIds,
            Long providerId,
            LocalDate validFrom
    );
}