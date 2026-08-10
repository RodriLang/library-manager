package com.rodrilang.librarymanager.invitation.dto;

import com.rodrilang.librarymanager.auth.enums.RoleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateBookstoreInvitationRequest(

        @NotNull
        Long bookstoreId,

        @Email
        String email,

        @NotNull
        RoleType role,

        @Min(1)
        @Max(168)
        Integer expirationHours

) {
}