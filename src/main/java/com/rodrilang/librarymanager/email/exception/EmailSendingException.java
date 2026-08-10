package com.rodrilang.librarymanager.email.exception;

import lombok.Getter;

@Getter
public class EmailSendingException extends RuntimeException {

    private final String recipient;
    private final String subject;

    public EmailSendingException(
            String recipient,
            String subject,
            Throwable cause) {

        super("No se pudo enviar el correo a " + recipient, cause);

        this.recipient = recipient;
        this.subject = subject;
    }
}