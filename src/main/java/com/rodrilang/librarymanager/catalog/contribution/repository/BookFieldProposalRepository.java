package com.rodrilang.librarymanager.catalog.contribution.repository;

import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldProposalStatus;
import com.rodrilang.librarymanager.catalog.contribution.model.BookFieldProposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface BookFieldProposalRepository extends JpaRepository<BookFieldProposal, Long> {

    boolean existsByBook_IdAndFieldAndSubmittedByBookstoreIdAndStatusAndProposedValue(
            Long bookId,
            BookField field,
            Long submittedByBookstoreId,
            BookFieldProposalStatus status,
            String proposedValue
    );


    java.util.List<BookFieldProposal> findAllByBook_IdAndSubmittedByBookstoreIdOrderByCreatedAtDesc(
            Long bookId,
            Long submittedByBookstoreId
    );

    java.util.Optional<BookFieldProposal> findFirstByBook_IdAndFieldAndSubmittedByBookstoreIdAndStatusAndProposedValueOrderByCreatedAtDesc(
            Long bookId,
            BookField field,
            Long submittedByBookstoreId,
            BookFieldProposalStatus status,
            String proposedValue
    );

    @Modifying
    @Query("""
            update BookFieldProposal p
               set p.status = :supersededStatus
             where p.book.id = :bookId
               and p.field = :field
               and p.submittedByBookstoreId = :bookstoreId
               and p.status = :pendingStatus
               and (:exceptProposalId is null or p.id <> :exceptProposalId)
            """)
    int supersedePendingForBookstore(
            @Param("bookId") Long bookId,
            @Param("field") BookField field,
            @Param("bookstoreId") Long bookstoreId,
            @Param("exceptProposalId") Long exceptProposalId,
            @Param("pendingStatus") BookFieldProposalStatus pendingStatus,
            @Param("supersededStatus") BookFieldProposalStatus supersededStatus
    );

    @Query("""
            select p
            from BookFieldProposal p
            join p.book b
            where (:status is null or p.status = :status)
              and (:bookId is null or b.id = :bookId)
              and (:field is null or p.field = :field)
            """)
    Page<BookFieldProposal> search(
            @Param("status") BookFieldProposalStatus status,
            @Param("bookId") Long bookId,
            @Param("field") BookField field,
            Pageable pageable
    );

    @Modifying
    @Query("""
            update BookFieldProposal p
               set p.status = :supersededStatus,
                   p.reviewedAt = :reviewedAt,
                   p.reviewedByUserId = :reviewedByUserId
             where p.book.id = :bookId
               and p.field = :field
               and p.status = :pendingStatus
               and p.id <> :approvedProposalId
            """)
    int supersedeOtherPending(
            @Param("bookId") Long bookId,
            @Param("field") BookField field,
            @Param("approvedProposalId") Long approvedProposalId,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("reviewedByUserId") Long reviewedByUserId,
            @Param("pendingStatus") BookFieldProposalStatus pendingStatus,
            @Param("supersededStatus") BookFieldProposalStatus supersededStatus
    );
}
