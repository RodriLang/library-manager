package com.rodrilang.librarymanager.inventory.consignment.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.InventoryMovement;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "consignment_settlement_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_consignment_settlement_items_movement", columnNames = "inventory_movement_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ConsignmentSettlementItem extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private ConsignmentSettlement settlement;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_movement_id", nullable = false)
    private InventoryMovement inventoryMovement;
    @Column(nullable = false)
    private Integer quantity;
}
