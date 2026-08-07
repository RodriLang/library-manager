package com.rodrilang.librarymanager.cover.job.entity;

import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.cover.job.enums.BookCoverJobErrorCode;
import com.rodrilang.librarymanager.cover.job.enums.BookCoverJobStatus;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.model.Book;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "book_cover_jobs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_book_cover_jobs_job_key",
                        columnNames = "job_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_book_cover_jobs_book_id",
                        columnList = "book_id"
                ),
                @Index(
                        name = "idx_book_cover_jobs_status",
                        columnList = "status"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCoverJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "book_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_book_cover_jobs_book"
            )
    )
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "price_list_import_job_id",
            foreignKey = @ForeignKey(
                    name = "fk_book_cover_jobs_price_list_import_job"
            )
    )
    private PriceListImportJob priceListImportJob;

    @Column(
            name = "source_url",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String sourceUrl;

    @Column(
            name = "normalized_source_url",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String normalizedSourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "source",
            nullable = false,
            length = 40
    )
    private BookCoverSource source;

    @Column(name = "source_row_number")
    private Integer sourceRowNumber;

    @Column(
            name = "job_key",
            nullable = false,
            length = 64
    )
    private String jobKey;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private BookCoverJobStatus status;

    @Column(
            name = "attempts",
            nullable = false
    )
    private int attempts;

    @Column(
            name = "max_attempts",
            nullable = false
    )
    private int maxAttempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "error_code",
            length = 50
    )
    private BookCoverJobErrorCode errorCode;

    @Column(
            name = "error_message",
            columnDefinition = "TEXT"
    )
    private String errorMessage;

    @Column(
            name = "cloudinary_public_id",
            length = 255
    )
    private String cloudinaryPublicId;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private BookCoverJob(
            Book book,
            PriceListImportJob priceListImportJob,
            String sourceUrl,
            String normalizedSourceUrl,
            BookCoverSource source,
            Integer sourceRowNumber,
            String jobKey,
            int maxAttempts
    ) {
        this.book = Objects.requireNonNull(
                book,
                "El libro es obligatorio"
        );

        this.priceListImportJob = priceListImportJob;
        this.sourceUrl = requireText(
                sourceUrl,
                "La URL original es obligatoria"
        );
        this.normalizedSourceUrl = requireText(
                normalizedSourceUrl,
                "La URL normalizada es obligatoria"
        );
        this.source = Objects.requireNonNull(
                source,
                "El origen de la portada es obligatorio"
        );
        this.sourceRowNumber = validateRow(sourceRowNumber);
        this.jobKey = requireHash(jobKey);
        this.status = BookCoverJobStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = validateMaxAttempts(maxAttempts);
    }

    public static BookCoverJob create(
            Book book,
            PriceListImportJob priceListImportJob,
            String sourceUrl,
            String normalizedSourceUrl,
            BookCoverSource source,
            Integer sourceRowNumber,
            String jobKey,
            int maxAttempts
    ) {
        return new BookCoverJob(
                book,
                priceListImportJob,
                sourceUrl,
                normalizedSourceUrl,
                source,
                sourceRowNumber,
                jobKey,
                maxAttempts
        );
    }

    public void markAsProcessing() {
        if (status != BookCoverJobStatus.PENDING) {
            throw new IllegalStateException(
                    "Solo un trabajo pendiente puede comenzar a procesarse"
            );
        }

        this.status = BookCoverJobStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
        this.attempts++;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markAsCompleted(String cloudinaryPublicId) {
        this.status = BookCoverJobStatus.COMPLETED;
        this.cloudinaryPublicId = normalizeOptionalText(
                cloudinaryPublicId
        );
        this.completedAt = LocalDateTime.now();
        this.nextAttemptAt = null;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markAsSkipped(String message) {
        this.status = BookCoverJobStatus.SKIPPED;
        this.completedAt = LocalDateTime.now();
        this.nextAttemptAt = null;
        this.errorCode = null;
        this.errorMessage = normalizeOptionalText(message);
    }

    public void scheduleRetry(
            BookCoverJobErrorCode errorCode,
            String errorMessage,
            LocalDateTime nextAttemptAt
    ) {
        if (attempts >= maxAttempts) {
            markAsFailed(errorCode, errorMessage);
            return;
        }

        this.status = BookCoverJobStatus.PENDING;
        this.errorCode = Objects.requireNonNull(errorCode);
        this.errorMessage = normalizeOptionalText(errorMessage);
        this.nextAttemptAt = Objects.requireNonNull(
                nextAttemptAt,
                "La fecha del próximo intento es obligatoria"
        );
    }

    public void markAsFailed(
            BookCoverJobErrorCode errorCode,
            String errorMessage
    ) {
        this.status = BookCoverJobStatus.FAILED;
        this.errorCode = Objects.requireNonNull(errorCode);
        this.errorMessage = normalizeOptionalText(errorMessage);
        this.nextAttemptAt = null;
        this.completedAt = LocalDateTime.now();
    }

    public void resetForManualRetry() {
        if (
                status != BookCoverJobStatus.FAILED
                        && status != BookCoverJobStatus.SKIPPED
        ) {
            throw new IllegalStateException(
                    "Solo un trabajo fallido u omitido puede reintentarse manualmente"
            );
        }

        this.status = BookCoverJobStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = null;
        this.errorCode = null;
        this.errorMessage = null;
        this.startedAt = null;
        this.completedAt = null;
    }

    public boolean canBeProcessed(LocalDateTime now) {
        return status == BookCoverJobStatus.PENDING
                && (
                nextAttemptAt == null
                        || !nextAttemptAt.isAfter(now)
        );
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (status == null) {
            status = BookCoverJobStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private static String requireText(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String requireHash(String value) {
        String normalized = requireText(
                value,
                "La clave idempotente es obligatoria"
        ).toLowerCase();

        if (!normalized.matches("^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException(
                    "La clave idempotente debe ser un SHA-256 hexadecimal"
            );
        }

        return normalized;
    }

    private static Integer validateRow(Integer value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(
                    "El número de fila debe ser mayor que cero"
            );
        }

        return value;
    }

    private static int validateMaxAttempts(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad máxima de intentos debe ser mayor que cero"
            );
        }

        return value;
    }
}