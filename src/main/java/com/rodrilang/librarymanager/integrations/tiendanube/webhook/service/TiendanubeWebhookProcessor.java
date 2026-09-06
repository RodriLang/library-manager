package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOrderService;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.dto.TiendanubeClaimedWebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeWebhookProcessor {

    private final TiendanubeOrderService orderService;
    private final TiendanubeWebhookCompletionService completionService;

    public void process(TiendanubeClaimedWebhookEvent event) {
        try {
            TiendanubeWebhookRequest request = new TiendanubeWebhookRequest(
                    event.storeId(),
                    event.event(),
                    event.resourceId()
            );

            switch (event.event()) {
                case "order/created" -> orderService.handleOrderCreated(request);
                case "order/paid" -> orderService.handleOrderPaid(request);
                case "order/cancelled" -> orderService.handleOrderCancelled(request);
                default ->
                        throw new IllegalStateException("Evento de webhook no soportado en procesamiento: " + event.event());
            }

            completionService.complete(event);

            log.info(
                    "Tiendanube webhook completed. eventId={} event={} storeId={} resourceId={} attempt={}",
                    event.id(), event.event(), event.storeId(), event.resourceId(), event.attemptCount()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Tiendanube webhook processing failed. eventId={} event={} storeId={} resourceId={} attempt={}",
                    event.id(), event.event(), event.storeId(), event.resourceId(), event.attemptCount(), exception
            );
            completionService.fail(event, exception);
        }
    }
}
