package com.rodrilang.librarymanager.model;

import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.enums.CoverCandidateStatus;
import com.rodrilang.librarymanager.enums.CoverSearchStatus;
import com.rodrilang.librarymanager.util.TextNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "books")
public class Book extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "isbn_10", length = 10)
    private String isbn10;

    @Column(name = "isbn_13", length = 13)
    private String isbn13;

    @Column(nullable = false)
    private String title;

    @Column(name = "title_sort", nullable = false)
    private String titleSort;

    @Column(name = "title_search", nullable = false)
    private String titleSearch;

    private String subtitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String language;

    private Integer pageCount;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(name = "publication_month")
    private Integer publicationMonth;

    @Column(length = 1000)
    private String coverUrl;

    private String coverSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoverSearchStatus coverSearchStatus;

    private Instant coverCheckedAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer coverSearchAttempts = 0;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "genre_name")
    private String genreName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private BookSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookCatalogStatus catalogStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_bookstore_id")
    private Bookstore createdByBookstore;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;

    @Column(name = "cover_candidate_url", length = 2000)
    private String coverCandidateUrl;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "cover_candidate_status",
            length = 30
    )
    private CoverCandidateStatus coverCandidateStatus;

    @Column(
            name = "cover_candidate_attempts",
            nullable = false
    )
    @Builder.Default
    private Integer coverCandidateAttempts = 0;

    @Column(name = "cover_candidate_next_attempt_at")
    private Instant coverCandidateNextAttemptAt;

    @Column(
            name = "cover_candidate_error",
            columnDefinition = "TEXT"
    )
    private String coverCandidateError;

    @Column(name = "cover_candidate_started_at")
    private Instant coverCandidateStartedAt;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "book_authors",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private Set<Author> authors = new HashSet<>();

    @Column(name = "weight_grams", precision = 10, scale = 2)
    private BigDecimal weightGrams;

    @Column(name = "width_cm", precision = 10, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 10, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "depth_cm", precision = 10, scale = 2)
    private BigDecimal depthCm;

    @PrePersist
    @PreUpdate
    private void normalizeFields() {
        this.titleSort = TextNormalizer.normalizeForSort(title);
        this.titleSearch = TextNormalizer.normalizeForSearch(title);

        if (isbn13 != null) {
            isbn13 = isbn13.trim();
        }

        if (isbn10 != null) {
            isbn10 = isbn10.trim().toUpperCase(Locale.ROOT);
        }
    }

    @Transient
    public String getPreferredIsbn() {
        return isbn13 != null ? isbn13 : isbn10;
    }

    public void updateCover(
            String coverUrl,
            String coverSource
    ) {
        this.coverUrl = normalizeNullableText(coverUrl);
        this.coverSource = normalizeNullableText(coverSource);

        if (this.coverUrl != null) {
            this.coverSearchStatus = CoverSearchStatus.FOUND;
            this.coverCheckedAt = Instant.now();
        }
    }

    public void clearCover() {
        this.coverUrl = null;
        this.coverSource = null;
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public void registerCoverCandidate(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return;
        }

        String normalized = sourceUrl.trim();

        if (normalized.equals(this.coverCandidateUrl)) {
            return;
        }

        this.coverCandidateUrl = normalized;
        this.coverCandidateStatus = CoverCandidateStatus.PENDING;
        this.coverCandidateAttempts = 0;
        this.coverCandidateNextAttemptAt = null;
        this.coverCandidateError = null;
    }

    public void completeCoverCandidate(
            String cloudinaryUrl,
            String coverSource
    ) {
        updateCover(cloudinaryUrl, coverSource);
        clearCoverCandidate();
    }

    public void clearCoverCandidate() {
        this.coverCandidateUrl = null;
        this.coverCandidateStatus = null;
        this.coverCandidateAttempts = 0;
        this.coverCandidateNextAttemptAt = null;
        this.coverCandidateError = null;
    }

    public void scheduleCoverCandidateRetry(
            String error,
            Instant nextAttemptAt
    ) {
        this.coverCandidateStatus = CoverCandidateStatus.PENDING;
        this.coverCandidateError = normalizeNullableText(error);
        this.coverCandidateNextAttemptAt = nextAttemptAt;
    }

    public void failCoverCandidate(String error) {
        this.coverCandidateStatus = CoverCandidateStatus.FAILED;
        this.coverCandidateError = normalizeNullableText(error);
        this.coverCandidateNextAttemptAt = null;
    }

    public boolean hasCoverCandidate() {
        return coverCandidateUrl != null
                && !coverCandidateUrl.isBlank();
    }

    public boolean hasManualCover() {
        return coverUrl != null
                && !coverUrl.isBlank()
                && BookCoverSource.MANUAL_UPLOAD.name()
                .equalsIgnoreCase(coverSource);
    }
}