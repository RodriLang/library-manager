package com.rodrilang.librarymanager.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        String apiUrl,
        String apiToken,
        String from,
        String fromName,
        String frontendUrl,
        String invitationPath,
        String passwordResetPath
) {
}
