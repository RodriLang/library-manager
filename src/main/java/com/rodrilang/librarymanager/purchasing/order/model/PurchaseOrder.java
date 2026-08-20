package com.rodrilang.librarymanager.purchasing.order.model;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "purchase_orders",
        indexes = {
                @Index(
                        name = "idx_purchase_orders_bookstore_status",
                        columnList = "bookstore_id, status"
                ),
                @Index(
                        name = "idx_purchase_orders_provider",
                        columnList = "provider_id"
                )
        }
)
public class PurchaseOrder extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "bookstore_id",
            nullable = false
    )
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "provider_id",
            nullable = false
    )
    private PriceListProvider provider;

    @Column(
            name = "order_number",
            nullable = false,
            length = 50
    )
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PurchaseOrderStatus status =
            PurchaseOrderStatus.DRAFT;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(length = 1000)
    private String notes;
}