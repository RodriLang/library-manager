package com.rodrilang.librarymanager.auth.dtos.response;

import com.rodrilang.librarymanager.auth.enums.RoleType;

public record RoleResponse(

        RoleType role
) {
}