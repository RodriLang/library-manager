package com.rodrilang.librarymanager.email.model;

import lombok.Builder;

@Builder
public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String plainTextBody
) {
}