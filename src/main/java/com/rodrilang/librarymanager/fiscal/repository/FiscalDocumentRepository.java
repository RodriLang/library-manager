package com.rodrilang.librarymanager.fiscal.repository;

import com.rodrilang.librarymanager.fiscal.model.FiscalDocument;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long> {

    @EntityGraph(attributePaths = {"sale", "createdBy"})
    Optional<FiscalDocument> findBySaleIdAndBookstoreId(Long saleId, Long bookstoreId);

    Optional<FiscalDocument> findBySaleIdAndDocumentType(
            Long saleId,
            FiscalDocumentType documentType
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"sale", "createdBy"})
    @Query("""
            SELECT document
            FROM FiscalDocument document
            WHERE document.sale.id = :saleId
              AND document.documentType = :documentType
            """)
    Optional<FiscalDocument> findBySaleIdAndDocumentTypeForUpdate(
            @Param("saleId") Long saleId,
            @Param("documentType") FiscalDocumentType documentType
    );

    boolean existsBySaleIdAndStatusIn(
            Long saleId,
            Collection<FiscalDocumentStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"sale", "createdBy"})
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

    @EntityGraph(attributePaths = {"sale", "createdBy"})
    Optional<FiscalDocument> findByIdAndBookstoreId(Long documentId, Long bookstoreId);
}
