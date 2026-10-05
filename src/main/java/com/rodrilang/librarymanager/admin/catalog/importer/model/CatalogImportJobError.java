package com.rodrilang.librarymanager.admin.catalog.importer.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "catalog_import_job_errors", indexes = @Index(name="idx_catalog_import_job_errors_job", columnList="job_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CatalogImportJobError {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private CatalogImportJob job;
    @Column(name = "row_number") private Integer rowNumber;
    @Column(length = 32) private String isbn;
    @Column(length = 500) private String title;
    @Column(name = "error_type", nullable = false, length = 40) private String errorType;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
