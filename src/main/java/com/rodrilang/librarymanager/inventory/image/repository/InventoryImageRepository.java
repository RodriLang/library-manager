package com.rodrilang.librarymanager.inventory.image.repository;

import com.rodrilang.librarymanager.inventory.image.model.InventoryImage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryImageRepository extends JpaRepository<InventoryImage, Long> {
    @EntityGraph(attributePaths = {"asset"})
    List<InventoryImage> findAllByInventoryIdOrderBySortOrderAsc(Long inventoryId);

    @EntityGraph(attributePaths = {"asset", "inventory", "inventory.book"})
    Optional<InventoryImage> findByIdAndInventoryId(Long id, Long inventoryId);

    boolean existsByInventoryIdAndExternalUrl(Long inventoryId, String externalUrl);

    long countByAssetId(Long assetId);
}
