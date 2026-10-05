package com.rodrilang.librarymanager.inventory.pricing.repository;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportProviderRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryPriceImportProviderRowRepository
        extends JpaRepository<InventoryPriceImportProviderRow, Long> {

    List<InventoryPriceImportProviderRow> findAllByPriceImportIdOrderByRowNumberAsc(Long importId);

    void deleteAllByPriceImportId(Long importId);
}
