package com.rodrilang.librarymanager.integrations.tiendanube.work.listener;

import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeConnectedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.event.TiendanubeWorkAvailableEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TiendanubeWorkAvailableListener {

    private final TiendanubeWorkSignal workSignal;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleWorkAvailable(TiendanubeWorkAvailableEvent event) {
        workSignal.wakeNow(event.type());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleConnected(TiendanubeConnectedEvent event) {
        wakeAll();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void handleApplicationReady() {
        wakeAll();
    }

    private void wakeAll() {
        for (TiendanubeWorkType type : TiendanubeWorkType.values()) {
            workSignal.wakeNow(type);
        }
    }
}
