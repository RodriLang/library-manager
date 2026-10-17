package com.rodrilang.librarymanager.shipping.shipment.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.store.order.model.StoreOrderItem;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_shipment_items", uniqueConstraints = @UniqueConstraint(name = "uq_store_shipment_item", columnNames = {"shipment_id", "order_item_id"}))
public class StoreShipmentItem extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shipment_id", nullable = false) private StoreShipment shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_item_id", nullable = false) private StoreOrderItem orderItem;
    @Column(nullable = false) private Integer quantity;
}
