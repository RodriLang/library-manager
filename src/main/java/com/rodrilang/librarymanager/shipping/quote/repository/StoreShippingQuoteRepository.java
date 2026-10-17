package com.rodrilang.librarymanager.shipping.quote.repository;

import com.rodrilang.librarymanager.shipping.quote.model.StoreShippingQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreShippingQuoteRepository extends JpaRepository<StoreShippingQuote, UUID> {
    Optional<StoreShippingQuote> findByIdAndStoreId(UUID id, Long storeId);
}
