package com.rodrilang.librarymanager.inventory.pricing.model;

import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "inventory_price_import_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_price_import_item_row",
                columnNames = {"import_id", "row_number"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPriceImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id", nullable = false)
    private InventoryPriceImport priceImport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id")
    private Inventory inventory;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(length = 32)
    private String isbn;

    @Column(length = 500)
    private String title;

    @Column(length = 500)
    private String author;

    @Column(name = "incoming_price", precision = 12, scale = 2)
    private BigDecimal incomingPrice;

    @Column(name = "current_price", precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "existing_scheduled_price", precision = 12, scale = 2)
    private BigDecimal existingScheduledPrice;

    @Column(name = "change_percent", precision = 12, scale = 2)
    private BigDecimal changePercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InventoryPriceImportClassification classification;

    @Column(name = "conflict_reason", columnDefinition = "TEXT")
    private String conflictReason;

    @Column(name = "selected_default", nullable = false)
    private boolean selectedDefault;

    @Column(name = "duplicate_group", nullable = false)
    @Builder.Default
    private boolean duplicateGroup = false;

    @Column(name = "discarded", nullable = false)
    @Builder.Default
    private boolean discarded = false;

    @Column(name = "applied", nullable = false)
    private boolean applied = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
