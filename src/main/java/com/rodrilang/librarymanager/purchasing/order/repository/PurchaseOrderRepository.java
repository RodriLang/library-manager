package com.rodrilang.librarymanager.purchasing.order.repository;

import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrder;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;
import io.micrometer.common.lang.NonNullApi;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.util.Optional;

@NonNullApi
public interface PurchaseOrderRepository
        extends JpaRepository<PurchaseOrder, Long>,
        JpaSpecificationExecutor<PurchaseOrder> {

    @EntityGraph(attributePaths = {
            "provider"
    })
    Optional<PurchaseOrder> findByIdAndBookstoreId(
            Long id,
            Long bookstoreId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "provider"
    })
    @Query("""
            SELECT po
            FROM PurchaseOrder po
            WHERE po.id = :orderId
              AND po.bookstore.id = :bookstoreId
              AND po.status = :status
            """)
    Optional<PurchaseOrder> findByIdAndBookstoreIdAndStatusForUpdate(
            @Param("orderId") Long orderId,
            @Param("bookstoreId") Long bookstoreId,
            @Param("status") PurchaseOrderStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "provider"
    })
    @Query("""
            SELECT po
            FROM PurchaseOrder po
            WHERE po.id = :orderId
              AND po.bookstore.id = :bookstoreId
            """)
    Optional<PurchaseOrder> findByIdAndBookstoreIdForUpdate(
            @Param("orderId") Long orderId,
            @Param("bookstoreId") Long bookstoreId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"provider"})
    @Query("""
            SELECT po
            FROM PurchaseOrder po
            WHERE po.bookstore.id = :bookstoreId
              AND po.provider.id = :providerId
              AND po.status = com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus.DRAFT
            """)
    Optional<PurchaseOrder> findDraftByBookstoreIdAndProviderIdForUpdate(
            @Param("bookstoreId") Long bookstoreId,
            @Param("providerId") Long providerId
    );

    @Override
    @EntityGraph(attributePaths = {
            "provider"
    })
    Page<PurchaseOrder> findAll(
            @Nullable Specification<PurchaseOrder> spec,
            Pageable pageable
    );
}