package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeOrderServiceImpl implements TiendanubeOrderService {

    private final TiendanubeClient client;
    private final TiendanubeOrderPersistenceService persistenceService;

    @Override
    public void handleOrderPaid(TiendanubeWebhookRequest request) {
        TiendanubeOrderResponse order = loadOrder(request);
        persistenceService.applyPaid(request, order);
    }

    @Override
    public void handleOrderCancelled(TiendanubeWebhookRequest request) {
        TiendanubeOrderResponse order = loadOrder(request);
        persistenceService.applyCancelled(request, order);
    }

    private TiendanubeOrderResponse loadOrder(TiendanubeWebhookRequest request) {
        log.info(
                "Fetching Tiendanube order for webhook. storeId={} orderId={} event={}",
                request.storeId(), request.id(), request.event()
        );

        TiendanubeOrderResponse order = client.getOrder(request.storeId(), request.id());

        if (order == null) {
            throw new BusinessException("Tiendanube no devolvió la orden: " + request.id());
        }

        if (order.id() == null || !order.id().equals(request.id())) {
            throw new BusinessException("Tiendanube devolvió una orden distinta a la solicitada");
        }

        if (order.products() == null || order.products().isEmpty()) {
            throw new BusinessException("La orden Tiendanube no contiene productos: " + request.id());
        }

        return order;
    }
}
