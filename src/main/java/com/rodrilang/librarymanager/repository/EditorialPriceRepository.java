package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.model.EditorialPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EditorialPriceRepository extends JpaRepository<EditorialPrice, Long> {

    Optional<EditorialPrice> findFirstByBookIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
            Long bookId,
            LocalDate date
    );

    Optional<EditorialPrice> findByBookIdAndSourceAndValidFrom(
            Long bookId,
            PriceListSource source,
            LocalDate validFrom
    );

    List<EditorialPrice> findByBookIdInAndSourceAndValidFrom(
            List<Long> bookIds,
            PriceListSource source,
            LocalDate validFrom
    );
}