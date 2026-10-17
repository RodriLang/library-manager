package com.rodrilang.librarymanager.shipping.returns.repository;
import com.rodrilang.librarymanager.shipping.returns.model.StoreReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface StoreReturnItemRepository extends JpaRepository<StoreReturnItem,Long> {
    List<StoreReturnItem> findAllByStoreReturnIdOrderByIdAsc(Long returnId);
    @Query("""
      select coalesce(sum(i.quantity), 0)
      from StoreReturnItem i
      where i.orderItem.id = :orderItemId
        and i.storeReturn.status not in (
          com.rodrilang.librarymanager.shipping.returns.model.StoreReturnStatus.CANCELLED,
          com.rodrilang.librarymanager.shipping.returns.model.StoreReturnStatus.REJECTED
        )
    """)
    Long sumActiveReturned(@Param("orderItemId") Long orderItemId);

    @Query("""
      select coalesce(sum(i.quantity), 0)
      from StoreReturnItem i
      where i.orderItem.id = :orderItemId
        and i.storeReturn.shipment.id = :shipmentId
        and i.storeReturn.status not in (
          com.rodrilang.librarymanager.shipping.returns.model.StoreReturnStatus.CANCELLED,
          com.rodrilang.librarymanager.shipping.returns.model.StoreReturnStatus.REJECTED
        )
    """)
    Long sumActiveReturnedForShipment(@Param("orderItemId") Long orderItemId, @Param("shipmentId") Long shipmentId);
}
