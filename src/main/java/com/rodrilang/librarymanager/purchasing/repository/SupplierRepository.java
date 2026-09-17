package com.rodrilang.librarymanager.purchasing.repository;

import com.rodrilang.librarymanager.purchasing.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findAllByBookstoreIdOrderByNameAsc(Long bookstoreId);
    Optional<Supplier> findByIdAndBookstoreId(Long id, Long bookstoreId);
    boolean existsByBookstoreIdAndNameIgnoreCase(Long bookstoreId, String name);
}
