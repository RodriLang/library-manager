package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeStockSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeStockSyncServiceImpl implements TiendanubeStockSyncService {

    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeClient client;

    @Override
    public void syncStockByBookId(Long bookId, Integer currentStock) {
        productLinkRepository.findByBookIdAndActiveTrue(bookId)
                .ifPresentOrElse(
                        link -> syncStock(link, currentStock),
                        () -> log.info("Libro sin vínculo Tiendanube. bookId={}", bookId)
                );
    }

    private void syncStock(TiendanubeProductLink link, Integer currentStock) {
        client.updateStock(
                link.getTiendanubeStoreId(),
                link.getTiendanubeProductId(),
                link.getTiendanubeVariantId(),
                currentStock
        );

        log.info("Stock sincronizado con Tiendanube. bookId={}, productId={}, variantId={}, stock={}",
                link.getBook().getId(),
                link.getTiendanubeProductId(),
                link.getTiendanubeVariantId(),
                currentStock
        );
    }
}