package com.rodrilang.librarymanager.integrations.tiendanube.job.entity;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "tiendanube_sync_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_tiendanube_sync_attempt_job_number", columnNames = {"job_id", "attempt_number"}),
        indexes = @Index(name = "idx_tiendanube_sync_attempts_job", columnList = "job_id,started_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TiendanubeSyncAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private TiendanubeSyncJob job;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "processing_token", nullable = false)
    private UUID processingToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TiendanubeJobAttemptStatus status = TiendanubeJobAttemptStatus.PROCESSING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_type", length = 120)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    private void prePersist() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}
