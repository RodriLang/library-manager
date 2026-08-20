package com.rodrilang.librarymanager.email.service;

import com.rodrilang.librarymanager.email.config.EmailProperties;
import com.rodrilang.librarymanager.email.model.EmailMessage;
import com.rodrilang.librarymanager.email.template.EmailTemplateBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailDeliveryService deliveryService;
    private final EmailTemplateBuilder templateBuilder;
    private final EmailProperties properties;

    public void sendBookstoreInvitation(
            String toEmail,
            String bookstoreName,
            String token
    ) {
        validateInvitationParameters(
                toEmail,
                bookstoreName,
                token
        );

        String invitationUrl = buildInvitationUrl(token);

        String htmlBody =
                templateBuilder.buildBookstoreInvitationTemplate(
                        bookstoreName,
                        invitationUrl
                );

        String plainTextBody =
                templateBuilder.buildBookstoreInvitationPlainText(
                        bookstoreName,
                        invitationUrl
                );

        EmailMessage message =
                EmailMessage.builder()
                        .to(toEmail)
                        .subject("Tu invitación a Anaquel")
                        .htmlBody(htmlBody)
                        .plainTextBody(plainTextBody)
                        .build();

        deliveryService.send(message);
    }

    public void sendPasswordReset(
            String toEmail,
            String token
    ) {
        validatePasswordResetParameters(
                toEmail,
                token
        );

        String resetPasswordUrl =
                buildPasswordResetUrl(token);

        String htmlBody =
                templateBuilder.buildPasswordResetTemplate(
                        resetPasswordUrl
                );

        String plainTextBody =
                templateBuilder.buildPasswordResetPlainText(
                        resetPasswordUrl
                );

        EmailMessage message =
                EmailMessage.builder()
                        .to(toEmail)
                        .subject("Restablecer tu contraseña de Anaquel")
                        .htmlBody(htmlBody)
                        .plainTextBody(plainTextBody)
                        .build();

        deliveryService.send(message);
    }

    private String buildInvitationUrl(String token) {
        return UriComponentsBuilder
                .fromUriString(properties.frontendUrl())
                .path(properties.invitationPath())
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
    }

    private String buildPasswordResetUrl(String token) {
        return UriComponentsBuilder
                .fromUriString(properties.frontendUrl())
                .path(properties.passwordResetPath())
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
    }

    private void validateInvitationParameters(
            String email,
            String bookstoreName,
            String token
    ) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "El correo electrónico es obligatorio"
            );
        }

        if (bookstoreName == null || bookstoreName.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre de la librería es obligatorio"
            );
        }

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "El token de invitación es obligatorio"
            );
        }
    }

    private void validatePasswordResetParameters(
            String email,
            String token
    ) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "El correo electrónico es obligatorio"
            );
        }

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "El token de recuperación es obligatorio"
            );
        }
    }
}