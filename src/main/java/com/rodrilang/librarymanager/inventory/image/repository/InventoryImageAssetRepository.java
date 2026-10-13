package com.rodrilang.librarymanager.inventory.image.repository;

import com.rodrilang.librarymanager.inventory.image.model.InventoryImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryImageAssetRepository extends JpaRepository<InventoryImageAsset, Long> {
    Optional<InventoryImageAsset> findByContentHash(String contentHash);
}
