package com.rodrilang.librarymanager.importer.price.configuration.model;

import com.rodrilang.librarymanager.importer.price.configuration.enums.ProviderBookIdentifierStatus;
import com.rodrilang.librarymanager.model.Book;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "provider_books",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provider_books_provider_book",
                        columnNames = {"provider_id", "book_id"}
                ),
                @UniqueConstraint(
                        name = "uk_provider_books_external_code",
                        columnNames = {"provider_id", "external_code"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "provider_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_provider_books_provider")
    )
    private PriceListProvider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "book_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_provider_books_book")
    )
    private Book book;

    @Column(name = "external_code", length = 100)
    private String externalCode;

    @Column(name = "reported_isbn", length = 32)
    private String reportedIsbn;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_status", length = 50)
    private ProviderBookIdentifierStatus identifierStatus;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}