package com.rodrilang.librarymanager.inventory.pricing.repository;

import com.rodrilang.librarymanager.inventory.pricing.model.BookstorePriceListFormat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookstorePriceListFormatRepository extends JpaRepository<BookstorePriceListFormat, Long> {
    List<BookstorePriceListFormat> findAllByBookstoreIdAndActiveTrueOrderByStandardDescNameAsc(Long bookstoreId);
    Optional<BookstorePriceListFormat> findByIdAndBookstoreId(Long id, Long bookstoreId);
    Optional<BookstorePriceListFormat> findByBookstoreIdAndStandardTrue(Long bookstoreId);
    boolean existsByBookstoreIdAndNameIgnoreCase(Long bookstoreId, String name);
}
