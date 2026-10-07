package com.rodrilang.librarymanager.inventory.pricing.repository;

import com.rodrilang.librarymanager.inventory.pricing.model.BookstorePriceListFormat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookstorePriceListFormatRepository extends JpaRepository<BookstorePriceListFormat, Long> {
    List<BookstorePriceListFormat> findAllByBookstoreIdAndActiveTrueOrderByStandardDescNameAsc(Long bookstoreId);
    List<BookstorePriceListFormat> findAllByBookstoreIdAndProviderIdAndActiveTrueOrderByNameAsc(Long bookstoreId, Long providerId);
    Optional<BookstorePriceListFormat> findByIdAndBookstoreId(Long id, Long bookstoreId);
    Optional<BookstorePriceListFormat> findByBookstoreIdAndStandardTrue(Long bookstoreId);
    boolean existsByBookstoreIdAndNameIgnoreCase(Long bookstoreId, String name);

    @Modifying
    @Query("update BookstorePriceListFormat format set format.active = false where format.provider.id = :providerId")
    int deactivateAllByProviderId(@Param("providerId") Long providerId);

    @Modifying
    @Query("update BookstorePriceListFormat format set format.active = false where format.bookstore.id = :bookstoreId and format.provider.id = :providerId")
    int deactivateAllByBookstoreIdAndProviderId(
            @Param("bookstoreId") Long bookstoreId,
            @Param("providerId") Long providerId
    );
}
