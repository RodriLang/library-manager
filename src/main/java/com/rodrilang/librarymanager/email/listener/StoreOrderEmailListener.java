package com.rodrilang.librarymanager.email.listener;

import com.rodrilang.librarymanager.email.service.EmailService;
import com.rodrilang.librarymanager.store.order.event.StoreOrderNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreOrderEmailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStoreOrderNotification(StoreOrderNotificationEvent event) {
        try {
            log.info(
                    "Sending store order email type={} orderId={} orderNumber={} recipient={}",
                    event.type(),
                    event.orderId(),
                    event.orderNumber(),
                    event.customerEmail()
            );
            emailService.sendStoreOrderNotification(event);
        } catch (RuntimeException exception) {
            // The commercial transaction is already committed. Email delivery must never
            // turn a valid order transition into an HTTP error or alter order state.
            log.error(
                    "Store order email failed type={} orderId={} orderNumber={} recipient={}",
                    event.type(),
                    event.orderId(),
                    event.orderNumber(),
                    event.customerEmail(),
                    exception
            );
        }
    }
}
