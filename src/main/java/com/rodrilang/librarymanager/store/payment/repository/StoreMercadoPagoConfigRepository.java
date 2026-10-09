package com.rodrilang.librarymanager.store.payment.repository;

import com.rodrilang.librarymanager.store.payment.model.StoreMercadoPagoConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreMercadoPagoConfigRepository extends JpaRepository<StoreMercadoPagoConfig, Long> {
    Optional<StoreMercadoPagoConfig> findByBookstoreId(Long bookstoreId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select config from StoreMercadoPagoConfig config join fetch config.bookstore where config.bookstore.id = :bookstoreId")
    Optional<StoreMercadoPagoConfig> findByBookstoreIdForUpdate(@Param("bookstoreId") Long bookstoreId);
}
