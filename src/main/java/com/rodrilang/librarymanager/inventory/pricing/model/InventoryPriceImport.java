package com.rodrilang.librarymanager.inventory.pricing.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_price_imports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPriceImport extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "format_id")
    private BookstorePriceListFormat format;

    @Column(name = "source_name", length = 150)
    private String sourceName;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryPriceImportStatus status;

    @Column(name = "normalized_file_public_id", length = 255)
    private String normalizedFilePublicId;

    @Column(name = "normalized_file_url", columnDefinition = "TEXT")
    private String normalizedFileUrl;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "matched_rows", nullable = false)
    @Builder.Default
    private Integer matchedRows = 0;

    @Column(name = "unmatched_rows", nullable = false)
    @Builder.Default
    private Integer unmatchedRows = 0;

    @Column(name = "new_price_rows", nullable = false)
    @Builder.Default
    private Integer newPriceRows = 0;

    @Column(name = "increase_rows", nullable = false)
    @Builder.Default
    private Integer increaseRows = 0;

    @Column(name = "decrease_rows", nullable = false)
    @Builder.Default
    private Integer decreaseRows = 0;

    @Column(name = "unchanged_rows", nullable = false)
    @Builder.Default
    private Integer unchangedRows = 0;

    @Column(name = "conflict_rows", nullable = false)
    @Builder.Default
    private Integer conflictRows = 0;

    @Column(name = "review_rows", nullable = false)
    @Builder.Default
    private Integer reviewRows = 0;

    @Column(name = "applied_rows", nullable = false)
    @Builder.Default
    private Integer appliedRows = 0;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "applied_at")
    private Instant appliedAt;
}
