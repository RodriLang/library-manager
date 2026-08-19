package com.rodrilang.librarymanager.purchasing.requirement.repository;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirement;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;
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
public interface PurchaseRequirementRepository
        extends JpaRepository<PurchaseRequirement, Long>,
        JpaSpecificationExecutor<PurchaseRequirement> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT pr
            FROM PurchaseRequirement pr
            WHERE pr.bookstore.id = :bookstoreId
              AND pr.book.id = :bookId
              AND pr.status = :status
            """)
    Optional<PurchaseRequirement> findByBookstoreAndBookAndStatusForUpdate(
            @Param("bookstoreId") Long bookstoreId,
            @Param("bookId") Long bookId,
            @Param("status") PurchaseRequirementStatus status
    );

    @EntityGraph(attributePaths = {
            "book",
            "book.publisher",
            "book.authors",
            "preferredProvider"
    })
    Optional<PurchaseRequirement> findByIdAndBookstoreId(
            Long id,
            Long bookstoreId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "book",
            "book.publisher",
            "book.authors",
            "preferredProvider"
    })
    @Query("""
            SELECT pr
            FROM PurchaseRequirement pr
            WHERE pr.id = :requirementId
              AND pr.bookstore.id = :bookstoreId
              AND pr.status = :status
            """)
    Optional<PurchaseRequirement> findByIdAndBookstoreIdAndStatusForUpdate(
            @Param("requirementId") Long requirementId,
            @Param("bookstoreId") Long bookstoreId,
            @Param("status") PurchaseRequirementStatus status
    );

    @EntityGraph(attributePaths = {
            "book",
            "preferredProvider"
    })
    Optional<PurchaseRequirement>
    findByBookstoreIdAndBookIdAndStatus(
            Long bookstoreId,
            Long bookId,
            PurchaseRequirementStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT requirement
            FROM PurchaseRequirement requirement
            WHERE requirement.id = :requirementId
              AND requirement.bookstore.id = :bookstoreId
            """)
    Optional<PurchaseRequirement> findByIdAndBookstoreIdForUpdate(
            @Param("requirementId") Long requirementId,
            @Param("bookstoreId") Long bookstoreId
    );

    @Override
    @EntityGraph(attributePaths = {
            "book",
            "preferredProvider"
    })
    Page<PurchaseRequirement> findAll(
            @Nullable Specification<PurchaseRequirement> spec,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "preferredProvider"
    })
    @Query("""
            SELECT pr
            FROM PurchaseRequirement pr
            WHERE pr.bookstore.id = :bookstoreId
              AND pr.book.id IN :bookIds
              AND pr.status = :status
            """)
    List<PurchaseRequirement> findByBookstoreAndBookIdsAndStatus(
            @Param("bookstoreId") Long bookstoreId,
            @Param("bookIds") Collection<Long> bookIds,
            @Param("status") PurchaseRequirementStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "book",
            "preferredProvider",
            "bookstore"
    })
    @Query("""
            SELECT requirement
            FROM PurchaseRequirement requirement
            WHERE requirement.id IN :ids
              AND requirement.bookstore.id = :bookstoreId
            """)
    List<PurchaseRequirement> findAllByIdsForUpdate(
            @Param("ids") Collection<Long> ids,
            @Param("bookstoreId") Long bookstoreId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "book",
            "preferredProvider",
            "bookstore"
    })
    @Query("""
            SELECT requirement
            FROM PurchaseRequirement requirement
            WHERE requirement.id IN :requirementIds
              AND requirement.bookstore.id = :bookstoreId
            """)
    List<PurchaseRequirement> findAllByIdsAndBookstoreIdForUpdate(
            @Param("requirementIds")
            Collection<Long> requirementIds,
            @Param("bookstoreId")
            Long bookstoreId
    );
}