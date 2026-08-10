package com.rodrilang.librarymanager.invitation.event;

import java.time.Instant;

public record BookstoreInvitationCreatedEvent(
        Long invitationId,
        String email,
        String bookstoreName,
        String token,
        Instant expiresAt
) {
}