package com.rodrilang.librarymanager.inventory.count.listener;

import com.rodrilang.librarymanager.editorialprice.event.EffectiveEditorialPriceChangedEvent;
import com.rodrilang.librarymanager.inventory.count.service.InventoryCountEditorialPriceReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InventoryCountEditorialPriceChangedListener {

    private final InventoryCountEditorialPriceReconciliationService reconciliationService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onPriceChanged(EffectiveEditorialPriceChangedEvent event) {
        reconciliationService.reconcile(event.bookIds());
    }
}
