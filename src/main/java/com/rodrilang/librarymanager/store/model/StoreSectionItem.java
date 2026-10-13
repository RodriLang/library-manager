package com.rodrilang.librarymanager.store.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_section_items", uniqueConstraints = @UniqueConstraint(name = "uq_store_section_item", columnNames = {"section_id", "inventory_id"}))
public class StoreSectionItem extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "section_id", nullable = false) private StoreSection section;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inventory_id", nullable = false) private Inventory inventory;
    @Builder.Default @Column(name = "display_order", nullable = false) private Integer displayOrder = 0;
}
