package com.rodrilang.librarymanager.purchasing.model;

import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "bookstore_provider_book_terms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bookstore_provider_book_terms",
                columnNames = {"bookstore_id", "provider_id", "book_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_bookstore_provider_book_terms_provider",
                        columnList = "bookstore_id,provider_id"
                ),
                @Index(
                        name = "idx_bookstore_provider_book_terms_book",
                        columnList = "book_id"
                )
        }
)
public class BookstoreProviderBookTerm extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "last_purchase_date")
    private LocalDate lastPurchaseDate;
}
