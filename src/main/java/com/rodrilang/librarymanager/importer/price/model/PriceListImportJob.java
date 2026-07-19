package com.rodrilang.librarymanager.importer.price.model;

import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "price_list_source", nullable = false)
    private PriceListSource priceListSource;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceListImportJobStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "created_books", nullable = false)
    private int createdBooks;

    @Column(name = "created_prices", nullable = false)
    private int createdPrices;

    @Column(name = "updated_prices", nullable = false)
    private int updatedPrices;

    @Column(name = "unchanged_prices", nullable = false)
    private int unchangedPrices;

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
}