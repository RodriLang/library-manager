package com.rodrilang.librarymanager.store.order.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_order_items", uniqueConstraints = @UniqueConstraint(name = "uq_store_order_inventory", columnNames = {"order_id", "inventory_id"}))
public class StoreOrderItem extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false) private StoreOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inventory_id", nullable = false) private Inventory inventory;
    @Column(nullable = false) private Integer quantity;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal subtotal;
    @Column(nullable = false, length = 500) private String title;
    @Column(length = 30) private String isbn;
}
