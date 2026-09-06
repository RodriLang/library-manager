package com.rodrilang.librarymanager.integrations.tiendanube.management.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.management.entity.TiendanubeBulkOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TiendanubeBulkOperationRepository extends JpaRepository<TiendanubeBulkOperation, Long> {

    Optional<TiendanubeBulkOperation> findByIdAndBookstoreId(Long id, Long bookstoreId);
}
