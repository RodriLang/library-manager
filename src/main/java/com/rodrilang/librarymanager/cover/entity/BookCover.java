package com.rodrilang.librarymanager.cover.entity;

import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.cover.enums.BookCoverStatus;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.media.storage.StoredImage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "book_covers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_book_covers_public_id",
                        columnNames = "public_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_book_covers_book_id",
                        columnList = "book_id"
                ),
                @Index(
                        name = "idx_book_covers_status",
                        columnList = "status"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "book_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_book_covers_book")
    )
    private Book book;

    @Column(
            name = "public_id",
            nullable = false,
            length = 255
    )
    private String publicId;

    @Column(
            name = "secure_url",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String secureUrl;

    @Column(
            name = "original_source_url",
            columnDefinition = "TEXT"
    )
    private String originalSourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "source",
            nullable = false,
            length = 40
    )
    private BookCoverSource source;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private BookCoverStatus status;

    @Column(
            name = "content_hash",
            length = 64
    )
    private String contentHash;

    @Column(
            name = "format",
            length = 20
    )
    private String format;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(
            name = "primary_cover",
            nullable = false
    )
    private boolean primaryCover;

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

    private BookCover(
            Book book,
            String publicId,
            String secureUrl,
            String originalSourceUrl,
            BookCoverSource source,
            String contentHash,
            String format,
            Integer width,
            Integer height,
            Long fileSize,
            boolean primaryCover
    ) {
        this.book = Objects.requireNonNull(
                book,
                "El libro es obligatorio"
        );

        this.publicId = requireText(
                publicId,
                "El publicId es obligatorio"
        );

        this.secureUrl = requireText(
                secureUrl,
                "La URL segura es obligatoria"
        );

        this.originalSourceUrl = normalizeOptionalText(
                originalSourceUrl
        );

        this.source = Objects.requireNonNull(
                source,
                "El origen de la portada es obligatorio"
        );

        this.status = BookCoverStatus.AVAILABLE;
        this.contentHash = normalizeHash(contentHash);
        this.format = normalizeOptionalText(format);
        this.width = validatePositive(width, "El ancho");
        this.height = validatePositive(height, "El alto");
        this.fileSize = validateNonNegative(fileSize);
        this.primaryCover = primaryCover;
    }

    public static BookCover create(
            Book book,
            StoredImage storedImage,
            BookCoverSource source,
            String originalSourceUrl,
            String contentHash,
            boolean primaryCover
    ) {
        Objects.requireNonNull(
                storedImage,
                "La imagen almacenada es obligatoria"
        );

        return new BookCover(
                book,
                storedImage.publicId(),
                storedImage.secureUrl(),
                originalSourceUrl,
                source,
                contentHash,
                storedImage.format(),
                storedImage.width(),
                storedImage.height(),
                storedImage.bytes(),
                primaryCover
        );
    }

    public void markAsPrimary() {
        ensureAvailable();
        this.primaryCover = true;
    }

    public void removeAsPrimary() {
        this.primaryCover = false;
    }

    public void markAsReplaced() {
        this.status = BookCoverStatus.REPLACED;
        this.primaryCover = false;
    }

    public void markAsDeleted() {
        this.status = BookCoverStatus.DELETED;
        this.primaryCover = false;
    }

    public boolean isAvailable() {
        return status == BookCoverStatus.AVAILABLE;
    }

    public boolean belongsToBook(Long bookId) {
        return bookId != null
                && book != null
                && Objects.equals(book.getId(), bookId);
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null) {
            this.status = BookCoverStatus.AVAILABLE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private void ensureAvailable() {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "Solo una portada disponible puede marcarse como principal"
            );
        }
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

    private static String normalizeHash(String value) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        normalized = normalized.toLowerCase();

        if (!normalized.matches("^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException(
                    "El hash debe ser un SHA-256 hexadecimal de 64 caracteres"
            );
        }

        return normalized;
    }

    private static Integer validatePositive(
            Integer value,
            String fieldName
    ) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " debe ser mayor que cero"
            );
        }

        return value;
    }

    private static Long validateNonNegative(Long value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(
                    "El tamaño del archivo no puede ser negativo"
            );
        }

        return value;
    }
}