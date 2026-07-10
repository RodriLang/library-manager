package com.rodrilang.librarymanager.integrations.tiendanube.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TiendanubeProcessedEventRepository extends JpaRepository<TiendanubeProcessedEvent, Long> {

    boolean existsByStoreIdAndResourceIdAndEvent(
            Long storeId,
            Long resourceId,
            String event
    );
}