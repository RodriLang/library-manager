package com.rodrilang.librarymanager.store.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_hero_items", uniqueConstraints = {
        @UniqueConstraint(name = "uq_store_hero_item_inventory", columnNames = {"store_id", "inventory_id"}),
        @UniqueConstraint(name = "uq_store_hero_item_order", columnNames = {"store_id", "display_order"})
})
public class StoreHeroItem extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", nullable = false) private BookstoreStore store;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inventory_id", nullable = false) private Inventory inventory;
    @Column(name = "display_order", nullable = false) private Integer displayOrder;
}
