package com.rodrilang.librarymanager.invitation.dto;

import com.rodrilang.librarymanager.auth.enums.RoleType;

import java.time.Instant;

public record CreateBookstoreInvitationResponse(

        Long id,

        Long bookstoreId,

        String bookstoreName,

        String email,

        RoleType role,

        String token,

        Instant expiresAt

) {
}