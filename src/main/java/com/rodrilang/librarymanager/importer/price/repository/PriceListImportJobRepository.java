package com.rodrilang.librarymanager.importer.price.repository;

import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;
import io.micrometer.common.lang.NonNullApi;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@NonNullApi
public interface PriceListImportJobRepository
        extends JpaRepository<PriceListImportJob,
        Long>, JpaSpecificationExecutor<PriceListImportJob> {

    Optional<PriceListImportJob> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"provider", "importConfig", "importConfig.mappings"})
    Optional<PriceListImportJob> findWithImportConfigById(Long id);

    List<PriceListImportJob> findByStatus(PriceListImportJobStatus status);

    @EntityGraph(attributePaths = "provider")
    @Query("""
            SELECT job
            FROM PriceListImportJob job
            WHERE job.id = :jobId
            """)
    Optional<PriceListImportJob> findByIdWithProvider(
            @Param("jobId") Long jobId
    );

    @Override
    @EntityGraph(attributePaths = "provider")
    Page<PriceListImportJob> findAll(
            @Nullable Specification<PriceListImportJob> spec,
            Pageable pageable
    );
}