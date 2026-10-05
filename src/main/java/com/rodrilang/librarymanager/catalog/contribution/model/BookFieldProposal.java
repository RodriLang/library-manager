package com.rodrilang.librarymanager.catalog.contribution.model;

import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldProposalStatus;
import com.rodrilang.librarymanager.model.Book;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "book_field_proposals")
public class BookFieldProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_name", nullable = false, length = 40)
    private BookField field;

    @Column(name = "current_value", columnDefinition = "TEXT")
    private String currentValue;

    @Column(name = "proposed_value", nullable = false, columnDefinition = "TEXT")
    private String proposedValue;

    @Column(name = "submitted_by_bookstore_id", nullable = false)
    private Long submittedByBookstoreId;

    @Column(name = "submitted_by_user_id", nullable = false)
    private Long submittedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookFieldProposalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = BookFieldProposalStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
