package com.rodrilang.librarymanager.store.order.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_stock_reservations", uniqueConstraints = @UniqueConstraint(name = "uq_store_reservation_order_item", columnNames = "order_item_id"))
public class StoreStockReservation extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false) private StoreOrder order;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_item_id", nullable = false) private StoreOrderItem orderItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inventory_id", nullable = false) private Inventory inventory;
    @Column(nullable = false) private Integer quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StoreReservationStatus status;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "released_at") private Instant releasedAt;
}
