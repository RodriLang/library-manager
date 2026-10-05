package com.rodrilang.librarymanager.admin.catalog.importer.repository;

import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportFormat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CatalogImportFormatRepository extends JpaRepository<CatalogImportFormat, Long> {
    @EntityGraph(attributePaths = {"provider", "mappings"})
    @Query("select f from CatalogImportFormat f where f.id = :id")
    Optional<CatalogImportFormat> findDetailedById(@Param("id") Long id);

    List<CatalogImportFormat> findAllByProviderIdAndActiveTrueOrderByNameAsc(Long providerId);
    List<CatalogImportFormat> findAllByActiveTrueOrderByProviderNameAscNameAsc();
}
