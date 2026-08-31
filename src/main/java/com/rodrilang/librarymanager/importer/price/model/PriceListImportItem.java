package com.rodrilang.librarymanager.importer.price.model;

import com.rodrilang.librarymanager.importer.price.enums.EditorialPriceChange;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportItemOperation;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.EditorialPrice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "price_list_import_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_price_list_import_items_job_book",
                columnNames = {"job_id", "book_id"}
        )
)
public class PriceListImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "job_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_price_list_import_items_job"
            )
    )
    private PriceListImportJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "book_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_price_list_import_items_book"
            )
    )
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "editorial_price_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_price_list_import_items_editorial_price"
            )
    )
    private EditorialPrice editorialPrice;

    @Column(
            name = "imported_price",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal importedPrice;

    @Column(
            name = "previous_price",
            precision = 12,
            scale = 2
    )
    private BigDecimal previousPrice;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "operation",
            nullable = false,
            length = 20
    )
    private PriceListImportItemOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "price_change",
            nullable = false,
            length = 20
    )
    private EditorialPriceChange priceChange;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}