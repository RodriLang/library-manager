package com.rodrilang.librarymanager.store.order.repository;

import com.rodrilang.librarymanager.store.order.model.StoreReservationStatus;
import com.rodrilang.librarymanager.store.order.model.StoreStockReservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface StoreStockReservationRepository extends JpaRepository<StoreStockReservation, Long> {
    List<StoreStockReservation> findAllByOrderIdOrderByIdAsc(Long orderId);

    @Query("""
            select coalesce(sum(r.quantity), 0)
            from StoreStockReservation r
            where r.inventory.id = :inventoryId
              and r.status = :status
              and (r.expiresAt is null or r.expiresAt > :now)
            """)
    Long sumActiveReserved(@Param("inventoryId") Long inventoryId,
                           @Param("status") StoreReservationStatus status,
                           @Param("now") Instant now);

    @Query("""
            select r.inventory.id as inventoryId, sum(r.quantity) as quantity
            from StoreStockReservation r
            where r.inventory.id in :inventoryIds
              and r.status = :status
              and (r.expiresAt is null or r.expiresAt > :now)
            group by r.inventory.id
            """)
    List<ReservedQuantityProjection> sumActiveByInventoryIds(@Param("inventoryIds") Collection<Long> inventoryIds,
                                                             @Param("status") StoreReservationStatus status,
                                                             @Param("now") Instant now);
}
