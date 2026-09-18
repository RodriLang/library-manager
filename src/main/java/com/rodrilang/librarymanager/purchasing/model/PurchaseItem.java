package com.rodrilang.librarymanager.purchasing.model;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Book;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "purchase_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_purchase_item_book_condition", columnNames = {"purchase_id", "book_id", "condition"}
))
public class PurchaseItem extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookCondition condition = BookCondition.NEW;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "editorial_price_snapshot", precision = 14, scale = 2)
    private BigDecimal editorialPriceSnapshot;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 16, scale = 2)
    private BigDecimal totalCost;
}
