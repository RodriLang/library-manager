package com.rodrilang.librarymanager.inventory.count.model;

import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Book;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_count_items")
public class InventoryCountItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InventoryCountSession session;

    @Column(name = "raw_identifier", length = 100)
    private String rawIdentifier;

    @Column(name = "normalized_identifier", length = 100)
    private String normalizedIdentifier;

    @Column(name = "isbn_10", length = 10)
    private String isbn10;

    @Column(name = "isbn_13", length = 13)
    private String isbn13;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_candidate_id")
    private CatalogCandidate catalogCandidate;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryCountItemStatus status;

    @Column(name = "sale_price_override", precision = 12, scale = 2)
    private BigDecimal salePriceOverride;

    @Column(name = "editorial_price_sync_override")
    private Boolean editorialPriceSyncOverride;

    @Column(name = "publish_on_tiendanube_override")
    private Boolean publishOnTiendanubeOverride;

    @Column(name = "tiendanube_price_sync_override")
    private Boolean tiendanubePriceSyncOverride;

    @Column(name = "minimum_stock_override")
    private Integer minimumStockOverride;

    @Column(name = "first_scanned_at", nullable = false)
    private Instant firstScannedAt;

    @Column(name = "last_scanned_at", nullable = false)
    private Instant lastScannedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;
}
