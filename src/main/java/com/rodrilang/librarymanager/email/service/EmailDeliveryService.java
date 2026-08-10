package com.rodrilang.librarymanager.email.service;

import com.rodrilang.librarymanager.email.exception.EmailSendingException;
import com.rodrilang.librarymanager.email.model.EmailMessage;
import com.rodrilang.librarymanager.email.provider.EmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private final EmailProvider emailProvider;

    @Retryable(
            retryFor = EmailSendingException.class,
            maxAttempts = 3,
            backoff = @Backoff(
                    delay = 1000,
                    multiplier = 2.0,
                    maxDelay = 5000
            )
    )

    public void send(EmailMessage message) {

        log.debug(
                "Sending email provider={} recipient={}",
                emailProvider.getName(),
                message.to()
        );

        emailProvider.send(message);
    }
}