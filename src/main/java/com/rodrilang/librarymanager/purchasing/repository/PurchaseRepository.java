package com.rodrilang.librarymanager.purchasing.repository;

import com.rodrilang.librarymanager.purchasing.model.Purchase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @EntityGraph(attributePaths = {"provider"})
    List<Purchase> findAllByBookstoreIdOrderByPurchaseDateDescIdDesc(Long bookstoreId);

    @EntityGraph(attributePaths = {"provider", "items", "items.book"})
    Optional<Purchase> findByIdAndBookstoreId(Long id, Long bookstoreId);
}
