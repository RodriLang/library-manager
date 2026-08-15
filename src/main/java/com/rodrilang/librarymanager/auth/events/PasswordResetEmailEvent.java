package com.rodrilang.librarymanager.auth.events;

public record PasswordResetEmailEvent(
        String email,
        String token
) {
}