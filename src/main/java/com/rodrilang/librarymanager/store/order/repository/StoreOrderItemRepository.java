package com.rodrilang.librarymanager.store.order.repository;

import com.rodrilang.librarymanager.store.order.model.StoreOrderItem;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface StoreOrderItemRepository extends JpaRepository<StoreOrderItem, Long> {
    @EntityGraph(attributePaths = {"inventory", "inventory.book"})
    List<StoreOrderItem> findAllByOrderIdOrderByIdAsc(Long orderId);
}
