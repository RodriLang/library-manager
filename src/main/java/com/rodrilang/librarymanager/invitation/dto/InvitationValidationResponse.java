
package com.rodrilang.librarymanager.invitation.dto;

import java.time.Instant;

public record InvitationValidationResponse(

        boolean valid,

        Long bookstoreId,

        String bookstoreName,

        String email,

        Instant expiresAt

) {
}