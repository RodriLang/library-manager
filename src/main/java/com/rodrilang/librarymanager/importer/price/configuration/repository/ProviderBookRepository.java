package com.rodrilang.librarymanager.importer.price.configuration.repository;

import com.rodrilang.librarymanager.dto.response.BookProviderResponse;
import com.rodrilang.librarymanager.importer.price.configuration.model.ProviderBook;
import com.rodrilang.librarymanager.purchasing.provider.repository.projection.BookAlternativeProviderProjection;
import io.micrometer.common.lang.NonNullApi;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@NonNullApi
public interface ProviderBookRepository
        extends JpaRepository<ProviderBook, Long>,
        JpaSpecificationExecutor<ProviderBook> {

    Optional<ProviderBook> findByProviderIdAndExternalCode(Long providerId, String externalCode);

    Optional<ProviderBook> findByProviderIdAndBookId(Long providerId, Long bookId);

    @EntityGraph(attributePaths = "book")
    List<ProviderBook> findByProviderIdAndExternalCodeIn(Long providerId, Collection<String> externalCodes);

    @Override
    @EntityGraph(attributePaths = {
            "book",
            "book.publisher",
            "book.authors",
            "provider"
    })
    Page<ProviderBook> findAll(
            @Nullable Specification<ProviderBook> spec,
            Pageable pageable
    );

    @Query("""
            SELECT
                pb.book.id AS bookId,
                pb.provider.id AS providerId,
                pb.provider.name AS providerName
            FROM ProviderBook pb
            WHERE pb.book.id IN :bookIds
              AND pb.active = true
              AND pb.provider.active = true
              AND pb.provider.id <> :excludedProviderId
            ORDER BY
                pb.book.id,
                pb.provider.name
            """)
    List<BookAlternativeProviderProjection> findAlternativeProviders(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("excludedProviderId") Long excludedProviderId
    );

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

    boolean existsByProviderIdAndBookIdAndActiveTrue(
            Long providerId,
            Long bookId
    );
}