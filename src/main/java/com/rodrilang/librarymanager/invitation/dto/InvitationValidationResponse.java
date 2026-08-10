
package com.rodrilang.librarymanager.invitation.dto;

import com.rodrilang.librarymanager.invitation.model.InvitationStatus;

import java.time.Instant;

public record InvitationValidationResponse(

        InvitationStatus status,

        Long bookstoreId,

        String bookstoreName,

        String email,

        Instant expiresAt

) {
}