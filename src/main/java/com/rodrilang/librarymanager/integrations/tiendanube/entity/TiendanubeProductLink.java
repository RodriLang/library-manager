package com.rodrilang.librarymanager.integrations.tiendanube.entity;

import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tiendanube_product_links")
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

    @Column(name = "tiendanube_image_id")
    private Long tiendanubeImageId;

    @Column(name = "last_synced_cover_url", columnDefinition = "TEXT")
    private String lastSyncedCoverUrl;

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