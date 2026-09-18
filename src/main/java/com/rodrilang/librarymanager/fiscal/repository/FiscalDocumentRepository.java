package com.rodrilang.librarymanager.fiscal.repository;

import com.rodrilang.librarymanager.fiscal.model.FiscalDocument;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long> {

    @EntityGraph(attributePaths = {
            "sale",
            "createdBy",
            "associatedDocument",
            "reversingDocument"
    })
    Optional<FiscalDocument> findBySaleIdAndBookstoreIdAndDocumentType(
            Long saleId,
            Long bookstoreId,
            FiscalDocumentType documentType
    );

    @EntityGraph(attributePaths = {
            "sale",
            "createdBy",
            "associatedDocument",
            "reversingDocument"
    })
    List<FiscalDocument> findAllBySaleIdAndBookstoreIdOrderByCreatedAtAsc(
            Long saleId,
            Long bookstoreId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "sale",
            "createdBy",
            "associatedDocument",
            "reversingDocument"
    })
    @Query("""
            SELECT document
            FROM FiscalDocument document
            WHERE document.sale.id = :saleId
              AND document.bookstore.id = :bookstoreId
              AND document.documentType = :documentType
            """)
    Optional<FiscalDocument> findBySaleIdAndBookstoreIdAndDocumentTypeForUpdate(
            @Param("saleId") Long saleId,
            @Param("bookstoreId") Long bookstoreId,
            @Param("documentType") FiscalDocumentType documentType
    );

    boolean existsBySaleIdAndStatusIn(
            Long saleId,
            Collection<FiscalDocumentStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "sale",
            "createdBy",
            "associatedDocument",
            "reversingDocument"
    })
    @Query("""
            SELECT document
            FROM FiscalDocument document
            WHERE document.id = :documentId
              AND document.bookstore.id = :bookstoreId
            """)
    Optional<FiscalDocument> findByIdAndBookstoreIdForUpdate(
            @Param("documentId") Long documentId,
            @Param("bookstoreId") Long bookstoreId
    );

    @EntityGraph(attributePaths = {
            "sale",
            "createdBy",
            "associatedDocument",
            "reversingDocument"
    })
    Optional<FiscalDocument> findByIdAndBookstoreId(Long documentId, Long bookstoreId);

    @EntityGraph(attributePaths = {
            "sale",
            "createdBy",
            "associatedDocument",
            "reversingDocument"
    })
    @Query("""
            SELECT document
            FROM FiscalDocument document
            WHERE document.bookstore.id = :bookstoreId
              AND (:documentType IS NULL OR document.documentType = :documentType)
              AND (:status IS NULL OR document.status = :status)
              AND (:from IS NULL OR document.issueDate >= :from)
              AND (:to IS NULL OR document.issueDate <= :to)
            ORDER BY document.issueDate DESC, document.id DESC
            """)
    Page<FiscalDocument> search(
            @Param("bookstoreId") Long bookstoreId,
            @Param("documentType") FiscalDocumentType documentType,
            @Param("status") FiscalDocumentStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );
}
