package com.rodrilang.librarymanager.admin.catalog.importer.repository;

import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportFormat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CatalogImportFormatRepository extends JpaRepository<CatalogImportFormat, Long> {
    List<CatalogImportFormat> findAllByProviderIdAndActiveTrueOrderByNameAsc(Long providerId);
    List<CatalogImportFormat> findAllByActiveTrueOrderByProviderNameAscNameAsc();
}
