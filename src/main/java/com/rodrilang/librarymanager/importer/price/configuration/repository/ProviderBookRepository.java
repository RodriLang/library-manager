package com.rodrilang.librarymanager.importer.price.configuration.repository;

import com.rodrilang.librarymanager.dto.response.BookProviderResponse;
import com.rodrilang.librarymanager.importer.price.configuration.model.ProviderBook;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProviderBookRepository extends JpaRepository<ProviderBook, Long> {

    Optional<ProviderBook> findByProviderIdAndExternalCode(Long providerId, String externalCode);

    Optional<ProviderBook> findByProviderIdAndBookId(Long providerId, Long bookId);

    @EntityGraph(attributePaths = "book")
    List<ProviderBook> findByProviderIdAndExternalCodeIn(Long providerId, Collection<String> externalCodes);

    List<ProviderBook> findByProviderIdAndBookIdIn(Long providerId, Collection<Long> bookIds);

    List<ProviderBook> findByProviderIdAndActiveTrue(Long providerId);

    List<ProviderBook> findByBookIdAndActiveTrue(Long bookId);

    List<ProviderBook> findByProviderIdAndReportedIsbn(Long providerId, String reportedIsbn);

    @Query("""
            SELECT new com.rodrilang.librarymanager.dto.response.BookProviderResponse(
                pb.provider.id,
                pb.provider.name,
                pb.provider.code,
                pb.externalCode,
                pb.reportedIsbn,
                pb.identifierStatus,
                pb.active,
                pb.lastSeenAt
            )
            FROM ProviderBook pb
            WHERE pb.book.id = :bookId
              AND pb.active = true
            ORDER BY pb.provider.name
            """)
    List<BookProviderResponse> findActiveProvidersByBookId(
            @Param("bookId") Long bookId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "provider",
            "book",
            "book.publisher"
    })
    @Query("""
            SELECT pb
            FROM ProviderBook pb
            WHERE pb.id = :id
            """)
    Optional<ProviderBook> findByIdForUpdate(
            @Param("id") Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "provider",
            "book",
            "book.publisher"
    })
    @Query("""
            SELECT pb
            FROM ProviderBook pb
            WHERE pb.provider.id = :providerId
              AND pb.book.id = :bookId
            """)
    Optional<ProviderBook> findByProviderIdAndBookIdForUpdate(
            @Param("providerId") Long providerId,
            @Param("bookId") Long bookId
    );

    boolean existsByBookId(Long bookId);
}