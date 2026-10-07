package com.rodrilang.librarymanager.catalog.contribution.model;

import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Book;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "bookstore_book_field_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bookstore_book_field_override",
                columnNames = {"bookstore_id", "book_id", "field_name"}
        )
)
public class BookstoreBookFieldOverride extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bookstore_id", nullable = false)
    private Long bookstoreId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_name", nullable = false, length = 40)
    private BookField field;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;
}
