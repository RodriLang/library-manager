package com.rodrilang.librarymanager.importer.price.model;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportPhase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(
        name = "price_list_import_jobs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_price_list_import_jobs_idempotency_key",
                        columnNames = "idempotency_key"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceListImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "provider_id",
            foreignKey = @ForeignKey(name = "fk_price_list_import_jobs_provider")
    )
    private PriceListProvider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "import_config_id",
            foreignKey = @ForeignKey(name = "fk_price_list_import_jobs_config")
    )
    private PriceListImportConfig importConfig;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceListImportJobStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "processed_books", nullable = false)
    @Builder.Default
    private int processedBooks = 0;

    @Column(name = "processed_prices",nullable = false)
    @Builder.Default
    private int processedPrices = 0;

    @Builder.Default
    @Column(name = "duplicate_book_rows", nullable = false)
    private int duplicateBookRows = 0;

    @Column(name = "created_books", nullable = false)
    private int createdBooks;

    @Column(name = "created_prices", nullable = false)
    private int createdPrices;

    @Column(name = "updated_prices", nullable = false)
    private int updatedPrices;

    @Column(name = "unchanged_prices", nullable = false)
    private int unchangedPrices;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 30)
    @Builder.Default
    private PriceListImportPhase phase = PriceListImportPhase.STAGING;

    @Builder.Default
    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows = 0;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {

        if (phase == null) {
            phase = PriceListImportPhase.STAGING;
        }

        if (status == null) {
            status = PriceListImportJobStatus.PENDING;
        }

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}