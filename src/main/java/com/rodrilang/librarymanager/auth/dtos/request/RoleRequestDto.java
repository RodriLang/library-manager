package com.rodrilang.librarymanager.auth.dtos.request;

import com.rodrilang.librarymanager.auth.enums.RoleType;
import jakarta.validation.constraints.NotNull;

public record RoleRequestDto(

        @NotNull
        RoleType name
) {
}