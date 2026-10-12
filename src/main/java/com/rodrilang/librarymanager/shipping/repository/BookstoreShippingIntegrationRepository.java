package com.rodrilang.librarymanager.shipping.repository;

import com.rodrilang.librarymanager.shipping.model.BookstoreShippingIntegration;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookstoreShippingIntegrationRepository extends JpaRepository<BookstoreShippingIntegration, Long> {
    List<BookstoreShippingIntegration> findAllByBookstoreId(Long bookstoreId);
    Optional<BookstoreShippingIntegration> findByBookstoreIdAndProvider(Long bookstoreId, ShippingProviderType provider);
}
