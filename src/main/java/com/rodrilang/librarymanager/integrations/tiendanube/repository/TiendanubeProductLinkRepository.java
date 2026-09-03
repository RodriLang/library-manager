package com.rodrilang.librarymanager.integrations.tiendanube.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TiendanubeProductLinkRepository extends JpaRepository<TiendanubeProductLink, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT link FROM TiendanubeProductLink link WHERE link.id = :linkId")
    Optional<TiendanubeProductLink> findByIdForUpdate(@Param("linkId") Long linkId);

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

    @EntityGraph(attributePaths = {
            "inventory",
            "inventory.bookstore"
    })
    List<TiendanubeProductLink> findAllByInventoryIdInAndActiveTrue(Collection<Long> inventoryIds);

    @EntityGraph(attributePaths = {
            "inventory",
            "inventory.book",
            "inventory.book.authors",
            "inventory.book.publisher"
    })
    List<TiendanubeProductLink>
    findAllByTiendanubeStoreIdAndTiendanubeVariantIdInAndActiveTrue(
            Long tiendanubeStoreId,
            Collection<Long> tiendanubeVariantIds
    );
}