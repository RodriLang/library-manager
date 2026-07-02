package com.rodrilang.librarymanager.integrations.tiendanube.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TiendanubeProductLinkRepository extends JpaRepository<TiendanubeProductLink, Long> {

    Optional<TiendanubeProductLink> findByBookIdAndActiveTrue(Long bookId);

    Optional<TiendanubeProductLink> findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(
            Long tiendanubeStoreId,
            Long tiendanubeVariantId
    );
}