package com.rodrilang.librarymanager.purchasing.repository;

import com.rodrilang.librarymanager.purchasing.model.BookstoreProviderBookTerm;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookstoreProviderBookTermRepository extends JpaRepository<BookstoreProviderBookTerm, Long> {

    Optional<BookstoreProviderBookTerm> findByBookstoreIdAndProviderIdAndBookId(
            Long bookstoreId,
            Long providerId,
            Long bookId
    );


    @EntityGraph(attributePaths = {"book", "provider", "lastPriceImport"})
    @Query("""
            select term
            from BookstoreProviderBookTerm term
            where term.bookstore.id = :bookstoreId
              and term.provider.id = :providerId
              and (term.discountPercentage is not null or term.lastPurchaseDate is not null)
            order by term.book.title asc
            """)
    List<BookstoreProviderBookTerm> findCommercialTermsByBookstoreIdAndProviderId(
            @Param("bookstoreId") Long bookstoreId,
            @Param("providerId") Long providerId
    );

    @EntityGraph(attributePaths = {"book", "provider", "lastPriceImport"})
    List<BookstoreProviderBookTerm> findAllByBookstoreIdAndProviderIdOrderByBookTitleAsc(
            Long bookstoreId,
            Long providerId
    );

    @EntityGraph(attributePaths = {"book", "provider", "lastPriceImport"})
    List<BookstoreProviderBookTerm> findAllByBookstoreIdAndProviderIdAndBookIdIn(
            Long bookstoreId,
            Long providerId,
            Collection<Long> bookIds
    );

    @EntityGraph(attributePaths = {"book", "provider", "lastPriceImport"})
    List<BookstoreProviderBookTerm> findAllByBookstoreIdAndBookIdIn(
            Long bookstoreId,
            Collection<Long> bookIds
    );
}
