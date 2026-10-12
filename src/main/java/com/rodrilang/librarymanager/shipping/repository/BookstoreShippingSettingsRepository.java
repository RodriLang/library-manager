package com.rodrilang.librarymanager.shipping.repository;

import com.rodrilang.librarymanager.shipping.model.BookstoreShippingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookstoreShippingSettingsRepository extends JpaRepository<BookstoreShippingSettings, Long> {
    Optional<BookstoreShippingSettings> findByBookstoreId(Long bookstoreId);
}
