package com.rodrilang.librarymanager.purchasing.repository;

import com.rodrilang.librarymanager.purchasing.model.Purchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @EntityGraph(attributePaths = {"provider"})
    List<Purchase> findAllByBookstoreIdOrderByPurchaseDateDescIdDesc(Long bookstoreId);

    @EntityGraph(attributePaths = {"provider", "items", "items.book"})
    Optional<Purchase> findByIdAndBookstoreId(Long id, Long bookstoreId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"provider", "items", "items.book"})
    @Query("""
            SELECT p
            FROM Purchase p
            WHERE p.id = :id
              AND p.bookstore.id = :bookstoreId
            """)
    Optional<Purchase> findByIdAndBookstoreIdForUpdate(
            @Param("id") Long id,
            @Param("bookstoreId") Long bookstoreId
    );
}
