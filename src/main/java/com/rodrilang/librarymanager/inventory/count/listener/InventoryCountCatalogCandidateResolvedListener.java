package com.rodrilang.librarymanager.inventory.count.listener;

import com.rodrilang.librarymanager.catalog.candidate.event.CatalogCandidateResolvedEvent;
import com.rodrilang.librarymanager.inventory.count.service.InventoryCountCandidateResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InventoryCountCatalogCandidateResolvedListener {

    private final InventoryCountCandidateResolutionService resolutionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResolved(CatalogCandidateResolvedEvent event) {
        resolutionService.resolve(event.candidateId(), event.bookId());
    }
}
