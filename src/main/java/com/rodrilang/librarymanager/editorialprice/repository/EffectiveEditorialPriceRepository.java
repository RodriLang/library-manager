package com.rodrilang.librarymanager.editorialprice.repository;

import com.rodrilang.librarymanager.editorialprice.enums.EffectiveEditorialPriceInvalidationReason;
import com.rodrilang.librarymanager.editorialprice.model.EffectiveEditorialPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EffectiveEditorialPriceRepository
        extends JpaRepository<EffectiveEditorialPrice, Long> {

    Optional<EffectiveEditorialPrice>
    findFirstByBookIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDescIdDesc(
            Long bookId,
            LocalDate date
    );

    List<EffectiveEditorialPrice> findByBookIdAndActiveTrueOrderByValidFromDescIdDesc(Long bookId);

    @Query(
            value = """
                    SELECT DISTINCT ON (eep.book_id)
                           eep.*
                    FROM effective_editorial_prices eep
                    WHERE eep.book_id IN (:bookIds)
                      AND eep.active = TRUE
                      AND eep.valid_from <= :date
                    ORDER BY
                        eep.book_id,
                        eep.valid_from DESC,
                        eep.id DESC
                    """,
            nativeQuery = true
    )
    List<EffectiveEditorialPrice> findCurrentByBookIds(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("date") LocalDate date
    );

    @Query(
            value = """
                    SELECT DISTINCT ON (eep.book_id)
                           eep.*
                    FROM effective_editorial_prices eep
                    WHERE eep.book_id IN (:bookIds)
                      AND eep.active = TRUE
                      AND eep.valid_from < :date
                    ORDER BY
                        eep.book_id,
                        eep.valid_from DESC,
                        eep.id DESC
                    """,
            nativeQuery = true
    )
    List<EffectiveEditorialPrice> findLatestBefore(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("date") LocalDate date
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = false
    )
    @Query("""
            UPDATE EffectiveEditorialPrice price
            SET price.active = false,
                price.invalidatedAt = :invalidatedAt,
                price.invalidationReason = :reason
            WHERE price.book.id IN :bookIds
              AND price.active = true
              AND price.validFrom >= :fromDate
            """)
    int invalidateFrom(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("invalidatedAt") Instant invalidatedAt,
            @Param("reason")
            EffectiveEditorialPriceInvalidationReason reason
    );
}