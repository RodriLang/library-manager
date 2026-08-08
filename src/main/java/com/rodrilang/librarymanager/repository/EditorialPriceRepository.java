package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.projection.EditorialPriceImportProjection;
import jakarta.persistence.LockModeType;
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

    Optional<EditorialPrice>
    findFirstByBookIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
            Long bookId,
            LocalDate date
    );

    Optional<EditorialPrice> findByBookIdAndProviderIdAndValidFrom(
            Long bookId,
            Long providerId,
            LocalDate validFrom
    );

    List<EditorialPrice> findByBookIdInAndProviderIdAndValidFrom(
            List<Long> bookIds,
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