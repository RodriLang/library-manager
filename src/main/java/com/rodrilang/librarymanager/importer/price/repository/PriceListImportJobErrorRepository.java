package com.rodrilang.librarymanager.importer.price.repository;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceListImportJobErrorRepository extends JpaRepository<PriceListImportJobError, Long> {

    List<PriceListImportJobError> findByJobIdOrderByRowNumberAsc(Long jobId, Pageable pageable);

    @Query("""
            SELECT error
            FROM PriceListImportJobError error
            WHERE error.job.id = :jobId
              AND (
                  :severity IS NULL
                  OR error.severity = :severity
              )
            """)
    Page<PriceListImportJobError> findHistoryErrors(
            @Param("jobId") Long jobId,
            @Param("severity") RowValidationSeverity severity,
            Pageable pageable
    );

    void deleteByJobId(Long jobId);
}