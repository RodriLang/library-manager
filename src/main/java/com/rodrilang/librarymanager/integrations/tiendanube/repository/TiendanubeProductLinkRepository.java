package com.rodrilang.librarymanager.integrations.tiendanube.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TiendanubeProductLinkRepository extends JpaRepository<TiendanubeProductLink, Long> {

    Optional<TiendanubeProductLink> findByInventoryIdAndActiveTrue(Long inventoryId);

    Optional<TiendanubeProductLink> findByInventoryIdAndTiendanubeStoreIdAndActiveTrue(
            Long inventoryId,
            Long tiendanubeStoreId
    );

    @EntityGraph(attributePaths = {
            "inventory",
            "inventory.book",
            "inventory.book.authors",
            "inventory.book.publisher"
    })
    Optional<TiendanubeProductLink>
    findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(
            Long tiendanubeStoreId,
            Long tiendanubeVariantId
    );

    @EntityGraph(attributePaths = {
            "inventory",
            "inventory.book",
            "inventory.book.authors"
    })
    Optional<TiendanubeProductLink> findWithInventoryBookByInventoryIdAndActiveTrue(Long inventoryId);
}