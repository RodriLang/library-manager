package com.rodrilang.librarymanager.integrations.tiendanube.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TiendanubeStoreRepository extends JpaRepository<TiendanubeStore, Long> {

    Optional<TiendanubeStore> findByBookstoreId(Long bookstoreId);

    Optional<TiendanubeStore> findByStoreIdAndActiveTrue(Long storeId);

    boolean existsByBookstoreIdAndActiveTrue(Long bookstoreId);
}