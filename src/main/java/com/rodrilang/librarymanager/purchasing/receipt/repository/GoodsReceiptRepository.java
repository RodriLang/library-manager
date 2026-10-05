package com.rodrilang.librarymanager.purchasing.receipt.repository;

import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceipt;
import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceiptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    @EntityGraph(attributePaths = {"provider", "purchaseOrder"})
    Page<GoodsReceipt> findAllByBookstoreId(Long bookstoreId, Pageable pageable);

    @EntityGraph(attributePaths = {"provider", "purchaseOrder"})
    Page<GoodsReceipt> findAllByBookstoreIdAndPurchaseOrderId(Long bookstoreId, Long purchaseOrderId, Pageable pageable);

    @EntityGraph(attributePaths = {"provider", "purchaseOrder"})
    Optional<GoodsReceipt> findByIdAndBookstoreId(Long id, Long bookstoreId);

    boolean existsByPurchaseOrderIdAndStatus(Long purchaseOrderId, GoodsReceiptStatus status);

    @EntityGraph(attributePaths = {"provider", "purchaseOrder"})
    Optional<GoodsReceipt> findFirstByPurchaseOrderIdAndStatusOrderByIdDesc(
            Long purchaseOrderId,
            GoodsReceiptStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"provider", "purchaseOrder"})
    @Query("""
            select r from GoodsReceipt r
            where r.id = :id and r.bookstore.id = :bookstoreId
            """)
    Optional<GoodsReceipt> findByIdAndBookstoreIdForUpdate(@Param("id") Long id, @Param("bookstoreId") Long bookstoreId);
}
