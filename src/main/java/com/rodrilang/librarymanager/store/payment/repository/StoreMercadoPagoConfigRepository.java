package com.rodrilang.librarymanager.store.payment.repository;

import com.rodrilang.librarymanager.store.payment.model.StoreMercadoPagoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StoreMercadoPagoConfigRepository extends JpaRepository<StoreMercadoPagoConfig, Long> {
    Optional<StoreMercadoPagoConfig> findByBookstoreId(Long bookstoreId);
}
