package com.rodrilang.librarymanager.integrations.tiendanube.job.entity;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "tiendanube_sync_jobs",
        indexes = {
                @Index(name = "idx_tiendanube_sync_jobs_claim", columnList = "status,next_attempt_at,lease_until,id"),
                @Index(name = "idx_tiendanube_sync_jobs_inventory", columnList = "inventory_id,created_at"),
                @Index(name = "idx_tiendanube_sync_jobs_store", columnList = "store_id,status,created_at"),
                @Index(name = "idx_tiendanube_sync_jobs_internal_store", columnList = "tiendanube_store_id,status,created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TiendanubeSyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bookstore_id", nullable = false)
    private Long bookstoreId;

    @Column(name = "tiendanube_store_id", nullable = false)
    private Long tiendanubeStoreId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private TiendanubeJobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TiendanubeJobStatus status = TiendanubeJobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private TiendanubeJobSource source;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "processing_token")
    private UUID processingToken;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_error_type", length = 120)
    private String lastErrorType;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }
}
