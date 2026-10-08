package com.rodrilang.librarymanager.store.order.repository;

import com.rodrilang.librarymanager.store.order.model.StoreOrder;
import com.rodrilang.librarymanager.store.order.model.StoreOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long>, JpaSpecificationExecutor<StoreOrder> {
    Optional<StoreOrder> findByStoreIdAndClientRequestId(Long storeId, UUID clientRequestId);
    boolean existsByOrderNumber(String orderNumber);
    Optional<StoreOrder> findByStorePublicIdAndOrderNumberAndTrackingToken(UUID publicStoreId, String orderNumber, UUID trackingToken);

    @EntityGraph(attributePaths = {"store", "bookstore"})
    Optional<StoreOrder> findByIdAndBookstoreId(Long id, Long bookstoreId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from StoreOrder o where o.id = :id and o.bookstore.id = :bookstoreId")
    Optional<StoreOrder> findByIdAndBookstoreIdForUpdate(@Param("id") Long id, @Param("bookstoreId") Long bookstoreId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from StoreOrder o where o.id = :id")
    Optional<StoreOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("select o.id from StoreOrder o where o.status = :status and o.reservationExpiresAt is not null and o.reservationExpiresAt <= :now")
    List<Long> findExpiredPendingIds(@Param("status") StoreOrderStatus status, @Param("now") java.time.Instant now);
}
