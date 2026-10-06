package com.rodrilang.librarymanager.inventory.consignment.repository;

import com.rodrilang.librarymanager.inventory.consignment.model.ConsignmentSettlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsignmentSettlementRepository extends JpaRepository<ConsignmentSettlement, Long> {
    Page<ConsignmentSettlement> findAllByBookstoreId(Long bookstoreId, Pageable pageable);
    Optional<ConsignmentSettlement> findByIdAndBookstoreId(Long id, Long bookstoreId);
}
