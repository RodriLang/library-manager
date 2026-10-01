package com.rodrilang.librarymanager.purchasing.preference.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.model.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "bookstore_book_provider_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bookstore_book_provider_preferences",
                columnNames = {"bookstore_id", "book_id"}
        ),
        indexes = {
                @Index(name = "idx_bookstore_book_provider_preferences_provider", columnList = "provider_id"),
                @Index(name = "idx_bookstore_book_provider_preferences_book", columnList = "book_id")
        }
)
public class BookstoreBookProviderPreference extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProviderPreferenceSource source;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
