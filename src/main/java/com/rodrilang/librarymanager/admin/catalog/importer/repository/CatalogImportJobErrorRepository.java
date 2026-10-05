package com.rodrilang.librarymanager.admin.catalog.importer.repository;

import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportJobError;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CatalogImportJobErrorRepository extends JpaRepository<CatalogImportJobError, Long> {
    List<CatalogImportJobError> findTop200ByJobIdOrderByIdAsc(Long jobId);
}
