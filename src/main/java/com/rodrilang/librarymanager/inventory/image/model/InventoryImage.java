package com.rodrilang.librarymanager.inventory.image.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_images")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryImage extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private InventoryImageAsset asset;

    @Column(name = "external_url", columnDefinition = "TEXT")
    private String externalUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryImageSource source;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public String resolvedUrl() {
        return source == InventoryImageSource.UPLOAD && asset != null
                ? asset.getSecureUrl()
                : externalUrl;
    }
}
