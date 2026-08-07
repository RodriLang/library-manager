package com.rodrilang.librarymanager.importer.price.repository;

import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceListImportJobRepository extends JpaRepository<PriceListImportJob, Long> {

    Optional<PriceListImportJob> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"provider", "importConfig", "importConfig.mappings"})
    Optional<PriceListImportJob> findWithImportConfigById(Long id);

    List<PriceListImportJob> findByStatus(PriceListImportJobStatus status);
}