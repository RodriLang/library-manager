package com.rodrilang.librarymanager.inventory.pricing.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "inventory_prices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_price_effective_from",
                columnNames = {"inventory_id", "effective_from"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPrice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InventoryPriceSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_import_id")
    private InventoryPriceImport priceImport;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;
}
