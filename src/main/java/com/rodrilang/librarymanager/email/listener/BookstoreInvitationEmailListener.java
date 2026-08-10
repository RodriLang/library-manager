package com.rodrilang.librarymanager.email.listener;

import com.rodrilang.librarymanager.email.service.EmailService;
import com.rodrilang.librarymanager.invitation.event.BookstoreInvitationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookstoreInvitationEmailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvitationCreated(BookstoreInvitationCreatedEvent event) {

        log.info(
                "Sending bookstore invitation email invitationId={} recipient={}",
                event.invitationId(),
                event.email()
        );

        emailService.sendBookstoreInvitation(
                event.email(),
                event.bookstoreName(),
                event.token()
        );
    }
}