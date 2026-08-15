package com.rodrilang.librarymanager.email.listener;

import com.rodrilang.librarymanager.auth.events.PasswordResetEmailEvent;
import com.rodrilang.librarymanager.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PasswordResetEmailListener {

    private final EmailService emailService;

    @Async("emailTaskExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(PasswordResetEmailEvent event) {
        emailService.sendPasswordReset(
                event.email(),
                event.token()
        );
    }
}