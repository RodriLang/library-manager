package com.rodrilang.librarymanager.purchasing.preference.repository;

import com.rodrilang.librarymanager.purchasing.preference.model.BookstoreBookProviderPreference;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookstoreBookProviderPreferenceRepository
        extends JpaRepository<BookstoreBookProviderPreference, Long> {

    @EntityGraph(attributePaths = {"provider"})
    Optional<BookstoreBookProviderPreference> findByBookstoreIdAndBookId(Long bookstoreId, Long bookId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"provider", "book", "bookstore"})
    @Query("""
            SELECT preference
            FROM BookstoreBookProviderPreference preference
            WHERE preference.bookstore.id = :bookstoreId
              AND preference.book.id = :bookId
            """)
    Optional<BookstoreBookProviderPreference> findForUpdate(
            @Param("bookstoreId") Long bookstoreId,
            @Param("bookId") Long bookId
    );
}
