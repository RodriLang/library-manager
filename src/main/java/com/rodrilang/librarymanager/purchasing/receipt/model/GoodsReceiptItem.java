package com.rodrilang.librarymanager.purchasing.receipt.model;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderItem;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "goods_receipt_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_goods_receipt_items_book_condition",
        columnNames = {"goods_receipt_id", "book_id", "condition"}
))
public class GoodsReceiptItem extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id")
    private PurchaseOrderItem purchaseOrderItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookCondition condition = BookCondition.NEW;

    @Column(name = "expected_quantity")
    private Integer expectedQuantity;

    @Column(name = "document_quantity")
    private Integer documentQuantity;

    @Builder.Default
    @Column(name = "scanned_quantity", nullable = false)
    private Integer scannedQuantity = 0;

    @Builder.Default
    @Column(name = "received_quantity", nullable = false)
    private Integer receivedQuantity = 0;

    @Builder.Default
    @Column(name = "consignment_quantity", nullable = false)
    private Integer consignmentQuantity = 0;

    @Column(length = 500)
    private String notes;
}
