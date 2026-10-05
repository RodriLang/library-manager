package com.rodrilang.librarymanager.inventory.pricing.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.model.Provider;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "bookstore_price_list_formats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bookstore_price_list_format_name",
                columnNames = {"bookstore_id", "name"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookstorePriceListFormat extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @Column(nullable = false)
    private boolean standard;

    @Column(name = "sheet_index", nullable = false)
    private Integer sheetIndex;

    @Column(name = "first_data_row_index", nullable = false)
    private Integer firstDataRowIndex;

    @Column(name = "isbn_column")
    private Integer isbnColumn;

    @Column(name = "title_column")
    private Integer titleColumn;

    @Column(name = "author_column")
    private Integer authorColumn;

    @Column(name = "publisher_column")
    private Integer publisherColumn;

    @Column(name = "price_column", nullable = false)
    private Integer priceColumn;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
