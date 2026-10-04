package com.rodrilang.librarymanager.invitation.repository;

import com.rodrilang.librarymanager.invitation.model.BookstoreInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookstoreInvitationRepository extends JpaRepository<BookstoreInvitation, Long> {

    Optional<BookstoreInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM BookstoreInvitation i
            JOIN FETCH i.bookstore
            WHERE i.tokenHash = :tokenHash
            """)
    Optional<BookstoreInvitation> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM BookstoreInvitation i
            WHERE i.id = :id
            """)
    Optional<BookstoreInvitation> findByIdForUpdate(
            @Param("id") Long id
    );

    @Query(
            value = """
                    SELECT i
                    FROM BookstoreInvitation i
                    JOIN FETCH i.bookstore
                    """,
            countQuery = """
                    SELECT COUNT(i)
                    FROM BookstoreInvitation i
                    """
    )
    Page<BookstoreInvitation> findAllForAdmin(Pageable pageable);

    List<BookstoreInvitation> findByBookstore_IdOrderByCreatedAtDesc(
            Long bookstoreId
    );
}