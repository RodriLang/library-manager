package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProcessedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProcessedEventRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOrderService;
import com.rodrilang.librarymanager.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeOrderServiceImpl implements TiendanubeOrderService {

    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeProcessedEventRepository processedEventRepository;
    private final InventoryService inventoryService;
    private final TiendanubeClient client;

    @Transactional
    @Override
    public void handleOrderPaid(TiendanubeWebhookRequest request) {
        processOrder(
                request,
                "pagada",
                inventoryService::decreaseStockByBookId
        );
    }

    @Transactional
    @Override
    public void handleOrderCancelled(TiendanubeWebhookRequest request) {
        processOrder(
                request,
                "cancelada",
                inventoryService::increaseStockByBookId
        );
    }

    private void processOrder(
            TiendanubeWebhookRequest request,
            String event,
            BiConsumer<Long, Integer> stockOperation
    ) {

        if (wasProcessed(request)) {
            log.info("Evento Tiendanube ya procesado. storeId={}, orderId={}, event={}",
                    request.storeId(),
                    request.id(),
                    request.event()
            );
            return;
        }

        log.info("Procesando orden {} Tiendanube. storeId={}, orderId={}",
                event,
                request.storeId(),
                request.id());

        TiendanubeOrderResponse order = getOrder(request);

        if (order == null || order.products() == null || order.products().isEmpty()) {
            log.warn("Orden Tiendanube {} sin productos. storeId={}, orderId={}",
                    event,
                    request.storeId(),
                    request.id());
            return;
        }

        log.info("Orden obtenida correctamente. orderId={}, products={}",
                order.id(),
                order.products().size());

        order.products().forEach(product ->
                processProduct(request, product, event, stockOperation)
        );

        markAsProcessed(request);
    }

    private void processProduct(
            TiendanubeWebhookRequest request,
            TiendanubeOrderProductResponse product,
            String event,
            BiConsumer<Long, Integer> stockOperation
    ) {
        log.info("Procesando producto. productId={}, variantId={}, sku={}, quantity={}",
                product.productId(),
                product.variantId(),
                product.sku(),
                product.quantity());

        var link = productLinkRepository
                .findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(
                        request.storeId(),
                        product.variantId())
                .orElseThrow(() -> new BusinessException(
                        "No existe vínculo para la variante Tiendanube: " + product.variantId()
                ));

        Long bookId = link.getBook().getId();

        stockOperation.accept(bookId, product.quantity());

        log.info("Stock actualizado. event={}, bookId={}, variantId={}, quantity={}",
                event,
                bookId,
                product.variantId(),
                product.quantity());
    }

    private TiendanubeOrderResponse getOrder(TiendanubeWebhookRequest request) {
        return client.getOrder(
                request.storeId(),
                request.id()
        );
    }

    private boolean wasProcessed(TiendanubeWebhookRequest request) {
        return processedEventRepository.existsByStoreIdAndResourceIdAndEvent(
                request.storeId(),
                request.id(),
                request.event()
        );
    }

    private void markAsProcessed(TiendanubeWebhookRequest request) {
        TiendanubeProcessedEvent event = TiendanubeProcessedEvent.builder()
                .storeId(request.storeId())
                .resourceId(request.id())
                .event(request.event())
                .processedAt(LocalDateTime.now(ZoneId.systemDefault()))
                .build();

        processedEventRepository.save(event);
    }
}