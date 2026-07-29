package com.rodrilang.librarymanager.integrations.tiendanube.entity;

import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "tiendanube_product_links",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tiendanube_store_variant",
                        columnNames = {"tiendanube_store_id", "tiendanube_variant_id"}
                ),
                @UniqueConstraint(
                        name = "uk_tiendanube_store_inventory",
                        columnNames = {"tiendanube_store_id", "inventory_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TiendanubeProductLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(name = "tiendanube_store_id", nullable = false)
    private Long tiendanubeStoreId;

    @Column(name = "tiendanube_product_id", nullable = false)
    private Long tiendanubeProductId;

    @Column(name = "tiendanube_variant_id", nullable = false)
    private Long tiendanubeVariantId;

    @Column(name = "sku")
    private String sku;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}