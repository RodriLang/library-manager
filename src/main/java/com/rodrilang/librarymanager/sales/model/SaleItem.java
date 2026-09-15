package com.rodrilang.librarymanager.sales.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "sale_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sale_items_sale_inventory",
                        columnNames = {"sale_id", "inventory_id"}
                )
        },
        indexes = {
                @Index(name = "idx_sale_items_sale", columnList = "sale_id"),
                @Index(name = "idx_sale_items_inventory", columnList = "inventory_id")
        }
)
public class SaleItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 20)
    private String isbn;
}
