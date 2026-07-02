package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOrderService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeWebhookServiceImpl implements TiendanubeWebhookService {

    private final TiendanubeOrderService orderService;

    @Override
    public void process(TiendanubeWebhookRequest request) {
        log.info("Webhook Tiendanube recibido. storeId={}, event={}, resourceId={}",
                request.storeId(),
                request.event(),
                request.id()
        );

        switch (request.event()) {
            case "order/paid" -> orderService.handleOrderPaid(request);
            case "order/cancelled" -> orderService.handleOrderCancelled(request);
            default -> log.info("Evento Tiendanube ignorado: {}", request.event());
        }
    }
}