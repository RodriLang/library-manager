package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.repository.TiendanubeProcessedEventClaimRepository;
import com.rodrilang.librarymanager.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeOrderPersistenceService {

    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeProcessedEventClaimRepository processedEventClaimRepository;
    private final InventoryService inventoryService;

    @Transactional
    public void applyCreated(TiendanubeWebhookRequest request, TiendanubeOrderResponse order) {
        applyStockDeduction(request, order, "creada");
    }

    @Transactional
    public void applyPaidFallback(TiendanubeWebhookRequest request, TiendanubeOrderResponse order) {
        applyStockDeduction(request, order, "pagada-fallback");
    }

    @Transactional
    public void applyCancelled(TiendanubeWebhookRequest request, TiendanubeOrderResponse order) {
        boolean claimed = processedEventClaimRepository.tryClaim(
                request.storeId(),
                request.id(),
                request.event(),
                Instant.now()
        );

        if (!claimed) {
            logAlreadyApplied(request);
            return;
        }

        applyProducts(
                request,
                order,
                "cancelada",
                (inventoryId, quantity) -> inventoryService.restoreTiendanubeCancelledOrderStock(
                        inventoryId,
                        quantity,
                        String.valueOf(request.id())
                )
        );
    }

    private void applyStockDeduction(TiendanubeWebhookRequest request, TiendanubeOrderResponse order,
                                     String eventDescription) {
        boolean claimed = processedEventClaimRepository.tryClaimOrderStockDeduction(
                request.storeId(),
                request.id(),
                Instant.now()
        );

        if (!claimed) {
            log.info(
                    "Stock Tiendanube order already deducted. storeId={} orderId={} sourceEvent={}",
                    request.storeId(), request.id(), request.event()
            );
            return;
        }

        applyProducts(
                request,
                order,
                eventDescription,
                (inventoryId, quantity) -> inventoryService.recordTiendanubeSale(
                        inventoryId,
                        quantity,
                        String.valueOf(request.id())
                )
        );
    }

    private void applyProducts(TiendanubeWebhookRequest request, TiendanubeOrderResponse order, String eventDescription,
                               BiConsumer<Long, Integer> stockOperation) {
        for (TiendanubeOrderProductResponse product : order.products()) {
            processProduct(request, product, eventDescription, stockOperation);
        }
    }

    private void logAlreadyApplied(TiendanubeWebhookRequest request) {
        log.info(
                "Evento Tiendanube ya aplicado. storeId={} orderId={} event={}",
                request.storeId(), request.id(), request.event()
        );
    }

    private void processProduct(TiendanubeWebhookRequest request, TiendanubeOrderProductResponse product,
                                String eventDescription, BiConsumer<Long, Integer> stockOperation) {
        if (product == null || product.variantId() == null) {
            throw new BusinessException("La orden Tiendanube contiene un producto sin variant_id");
        }

        if (product.quantity() == null || product.quantity() <= 0) {
            throw new BusinessException("La orden Tiendanube contiene una cantidad inválida para variant_id: " + product.variantId());
        }

        TiendanubeProductLink link = productLinkRepository
                .findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(request.storeId(), product.variantId())
                .orElseThrow(() -> new BusinessException(
                        "No existe un vínculo activo para la variante Tiendanube: " + product.variantId()
                ));

        Long inventoryId = link.getInventory().getId();
        stockOperation.accept(inventoryId, product.quantity());

        log.info(
                "Stock local actualizado desde Tiendanube. event={} inventoryId={} productId={} variantId={} quantity={}",
                eventDescription, inventoryId, product.productId(), product.variantId(), product.quantity()
        );
    }
}
