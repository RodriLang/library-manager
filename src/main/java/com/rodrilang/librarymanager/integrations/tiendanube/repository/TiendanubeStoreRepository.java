package com.rodrilang.librarymanager.integrations.tiendanube.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TiendanubeStoreRepository extends JpaRepository<TiendanubeStore, Long> {

    Optional<TiendanubeStore> findByBookstoreId(Long bookstoreId);

    Optional<TiendanubeStore> findByStoreId(Long storeId);

    Optional<TiendanubeStore> findByBookstoreIdAndActiveTrue(Long bookstoreId);

    Optional<TiendanubeStore> findByStoreIdAndActiveTrue(Long storeId);

    List<TiendanubeStore> findAllByStoreIdInAndActiveTrue(Collection<Long> storeIds);

    boolean existsByStoreIdAndActiveTrueAndTokenValidTrue(Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT store FROM TiendanubeStore store WHERE store.id = :id")
    Optional<TiendanubeStore> findByIdForUpdate(@Param("id") Long id);
}
