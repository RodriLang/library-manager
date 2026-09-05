package com.rodrilang.librarymanager.integrations.tiendanube.listener;

import com.rodrilang.librarymanager.integrations.tiendanube.event.BookPublicationChangedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TiendanubeBookPublicationChangedListener {

    private final TiendanubeJobRequestService jobRequestService;

    @TransactionalEventListener(
            phase = TransactionPhase.BEFORE_COMMIT,
            fallbackExecution = true
    )
    public void handle(BookPublicationChangedEvent event) {
        jobRequestService.enqueueAutomaticPublicationSyncByBookId(event.bookId());
    }
}
