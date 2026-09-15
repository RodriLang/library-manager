package com.rodrilang.librarymanager.sales.repository;

import com.rodrilang.librarymanager.sales.model.Sale;
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
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    @Override
    @EntityGraph(attributePaths = {"createdBy"})
    Page<Sale> findAll(Specification<Sale> specification, Pageable pageable);

    @EntityGraph(attributePaths = {
            "createdBy",
            "cancelledBy"
    })
    Optional<Sale> findByIdAndBookstoreId(Long id, Long bookstoreId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "createdBy",
            "cancelledBy"
    })
    @Query("""
            SELECT sale
            FROM Sale sale
            WHERE sale.id = :saleId
              AND sale.bookstore.id = :bookstoreId
            """)
    Optional<Sale> findByIdAndBookstoreIdForUpdate(
            @Param("saleId") Long saleId,
            @Param("bookstoreId") Long bookstoreId
    );
}
