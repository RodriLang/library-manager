package com.rodrilang.librarymanager.admin.invitation.dto;
import com.rodrilang.librarymanager.auth.enums.RoleType;
import java.time.Instant;
public record AdminInvitationResponse(Long id, Long bookstoreId, String bookstoreName, String email, RoleType role, String status, Instant expiresAt, Instant createdAt) {}
