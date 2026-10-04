package com.rodrilang.librarymanager.admin.audit.dto;
import java.time.Instant;
public record AdminAuditLogResponse(Long id, Long actorUserId, Long bookstoreId, String action, String entityType, String entityId, String summary, Instant createdAt) {}
