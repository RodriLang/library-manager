package com.rodrilang.librarymanager.inventory.bulk.repository;

import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkOperation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryBulkOperationRepository extends JpaRepository<InventoryBulkOperation, Long> {
}