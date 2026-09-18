package com.rodrilang.librarymanager.purchasing.repository;

import com.rodrilang.librarymanager.purchasing.model.BookstoreProviderBookTerm;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookstoreProviderBookTermRepository extends JpaRepository<BookstoreProviderBookTerm, Long> {

    Optional<BookstoreProviderBookTerm> findByBookstoreIdAndProviderIdAndBookId(
            Long bookstoreId,
            Long providerId,
            Long bookId
    );

    @EntityGraph(attributePaths = {"book", "provider"})
    List<BookstoreProviderBookTerm> findAllByBookstoreIdAndProviderIdOrderByBookTitleAsc(
            Long bookstoreId,
            Long providerId
    );
}
