package com.rodrilang.librarymanager.integrations.tiendanube.event;

import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TiendanubePublicationRequestedListener {

    private final TiendanubeProductService productService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TiendanubePublicationRequestedEvent event) {
        try {
            productService.publishInventory(event.inventoryId());
        } catch (RuntimeException exception) {
            log.error("No se pudo publicar automáticamente el inventario en Tiendanube. inventoryId={}",
                    event.inventoryId(), exception);
        }
    }
}