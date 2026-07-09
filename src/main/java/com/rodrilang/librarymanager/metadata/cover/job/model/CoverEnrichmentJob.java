package com.rodrilang.librarymanager.metadata.cover.job.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "cover_enrichment_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverEnrichmentJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoverEnrichmentJobStatus status;

    private int totalBooks;

    private int processedBooks;

    private int foundCovers;

    private int notFoundCovers;

    private int errorCount;

    private int progressPercentage;

    private String errorMessage;

    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}