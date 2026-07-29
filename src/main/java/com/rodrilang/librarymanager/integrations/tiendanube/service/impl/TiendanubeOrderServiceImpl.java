package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProcessedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProcessedEventRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOrderService;
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
public class TiendanubeOrderServiceImpl implements TiendanubeOrderService {

    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeProcessedEventRepository processedEventRepository;
    private final InventoryService inventoryService;
    private final TiendanubeClient client;

    @Override
    @Transactional
    public void handleOrderPaid(TiendanubeWebhookRequest request) {
        processOrder(
                request,
                "pagada",
                inventoryService::decreaseStockFromTiendanube
        );
    }

    @Override
    @Transactional
    public void handleOrderCancelled(TiendanubeWebhookRequest request) {
        processOrder(
                request,
                "cancelada",
                inventoryService::increaseStockFromTiendanube
        );
    }

    private void processOrder(
            TiendanubeWebhookRequest request,
            String eventDescription,
            BiConsumer<Long, Integer> stockOperation
    ) {
        if (wasProcessed(request)) {
            log.info(
                    "Evento Tiendanube ya procesado. storeId={}, orderId={}, event={}",
                    request.storeId(),
                    request.id(),
                    request.event()
            );
            return;
        }

        log.info(
                "Procesando orden {} de Tiendanube. storeId={}, orderId={}",
                eventDescription,
                request.storeId(),
                request.id()
        );

        TiendanubeOrderResponse order = getOrder(request);

        if (order == null
                || order.products() == null
                || order.products().isEmpty()) {

            log.warn(
                    "Orden Tiendanube {} sin productos. storeId={}, orderId={}",
                    eventDescription,
                    request.storeId(),
                    request.id()
            );

            return;
        }

        log.info(
                "Orden obtenida correctamente. orderId={}, products={}",
                order.id(),
                order.products().size()
        );

        order.products().forEach(product ->
                processProduct(
                        request,
                        product,
                        eventDescription,
                        stockOperation
                )
        );

        markAsProcessed(request);
    }

    private void processProduct(
            TiendanubeWebhookRequest request,
            TiendanubeOrderProductResponse product,
            String eventDescription,
            BiConsumer<Long, Integer> stockOperation
    ) {
        log.info(
                "Procesando producto de Tiendanube. productId={}, variantId={}, sku={}, quantity={}",
                product.productId(),
                product.variantId(),
                product.sku(),
                product.quantity()
        );

        TiendanubeProductLink link = productLinkRepository
                .findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(request.storeId(), product.variantId())
                .orElseThrow(() -> new BusinessException(
                        "No existe un vínculo activo para la variante Tiendanube: " + product.variantId()));

        Long inventoryId = link.getInventory().getId();

        stockOperation.accept(
                inventoryId,
                product.quantity()
        );

        log.info(
                "Stock local actualizado desde Tiendanube. event={}, inventoryId={}, productId={}, variantId={}, quantity={}",
                eventDescription,
                inventoryId,
                product.productId(),
                product.variantId(),
                product.quantity()
        );
    }

    private TiendanubeOrderResponse getOrder(
            TiendanubeWebhookRequest request
    ) {
        return client.getOrder(
                request.storeId(),
                request.id()
        );
    }

    private boolean wasProcessed(
            TiendanubeWebhookRequest request
    ) {
        return processedEventRepository
                .existsByStoreIdAndResourceIdAndEvent(
                        request.storeId(),
                        request.id(),
                        request.event()
                );
    }

    private void markAsProcessed(
            TiendanubeWebhookRequest request
    ) {
        TiendanubeProcessedEvent processedEvent =
                TiendanubeProcessedEvent.builder()
                        .storeId(request.storeId())
                        .resourceId(request.id())
                        .event(request.event())
                        .processedAt(Instant.now())
                        .build();

        processedEventRepository.save(processedEvent);
    }
}