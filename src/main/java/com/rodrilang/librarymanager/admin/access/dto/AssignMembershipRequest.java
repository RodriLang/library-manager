package com.rodrilang.librarymanager.admin.access.dto;

import com.rodrilang.librarymanager.auth.enums.RoleType;
import jakarta.validation.constraints.NotNull;

public record AssignMembershipRequest(@NotNull Long bookstoreId, @NotNull RoleType role) {}
