package com.rodrilang.librarymanager.purchasing.receipt.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrder;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "goods_receipts", indexes = {
        @Index(name = "idx_goods_receipts_bookstore_status", columnList = "bookstore_id,status"),
        @Index(name = "idx_goods_receipts_order", columnList = "purchase_order_id"),
        @Index(name = "idx_goods_receipts_provider", columnList = "provider_id")
})
public class GoodsReceipt extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @Column(name = "receipt_number", nullable = false, length = 50)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsReceiptSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GoodsReceiptStatus status = GoodsReceiptStatus.DRAFT;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(length = 1000)
    private String notes;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
