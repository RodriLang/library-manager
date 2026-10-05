package com.rodrilang.librarymanager.admin.catalog.importer.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.provider.model.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "catalog_import_jobs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CatalogImportJob extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "format_id", nullable = false)
    private CatalogImportFormat format;

    @Column(name = "requested_by_user_id") private Long requestedByUserId;
    @Column(name = "original_filename", length = 255) private String originalFilename;
    @Column(name = "temporary_file_path", length = 1000) private String temporaryFilePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CatalogImportStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CatalogImportPhase phase;

    @Builder.Default @Column(name = "total_rows", nullable = false) private int totalRows = 0;
    @Builder.Default @Column(name = "processed_rows", nullable = false) private int processedRows = 0;
    @Builder.Default @Column(name = "created_books", nullable = false) private int createdBooks = 0;
    @Builder.Default @Column(name = "enriched_books", nullable = false) private int enrichedBooks = 0;
    @Builder.Default @Column(name = "unchanged_books", nullable = false) private int unchangedBooks = 0;
    @Builder.Default @Column(name = "conflicted_books", nullable = false) private int conflictedBooks = 0;
    @Builder.Default @Column(name = "skipped_rows", nullable = false) private int skippedRows = 0;
    @Builder.Default @Column(name = "error_count", nullable = false) private int errorCount = 0;
    @Builder.Default @Column(name = "provider_links_created", nullable = false) private int providerLinksCreated = 0;
    @Builder.Default @Column(name = "provider_links_updated", nullable = false) private int providerLinksUpdated = 0;

    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "failure_message", columnDefinition = "TEXT") private String failureMessage;
}
