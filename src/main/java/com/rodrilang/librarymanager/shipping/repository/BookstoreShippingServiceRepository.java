package com.rodrilang.librarymanager.shipping.repository;

import com.rodrilang.librarymanager.shipping.model.BookstoreShippingService;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookstoreShippingServiceRepository extends JpaRepository<BookstoreShippingService, Long> {
    List<BookstoreShippingService> findAllByBookstoreIdOrderBySortOrderAscIdAsc(Long bookstoreId);
    List<BookstoreShippingService> findAllByBookstoreIdAndProviderOrderBySortOrderAscIdAsc(Long bookstoreId, ShippingProviderType provider);
    Optional<BookstoreShippingService> findByBookstoreIdAndProviderAndServiceCode(Long bookstoreId, ShippingProviderType provider, String serviceCode);
}
