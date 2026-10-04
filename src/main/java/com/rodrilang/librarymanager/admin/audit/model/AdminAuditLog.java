package com.rodrilang.librarymanager.admin.audit.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "admin_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="actor_user_id") private Long actorUserId;
    @Column(name="bookstore_id") private Long bookstoreId;
    @Column(nullable=false, length=100) private String action;
    @Column(name="entity_type", nullable=false, length=80) private String entityType;
    @Column(name="entity_id", length=120) private String entityId;
    @Column(length=500) private String summary;
    @Column(name="before_data", columnDefinition="TEXT") private String beforeData;
    @Column(name="after_data", columnDefinition="TEXT") private String afterData;
    @Column(length=80) private String ip;
    @Column(name="created_at", nullable=false, updatable=false) @Builder.Default private Instant createdAt = Instant.now();
}
