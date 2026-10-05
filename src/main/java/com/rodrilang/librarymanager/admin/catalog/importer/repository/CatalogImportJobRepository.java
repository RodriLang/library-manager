package com.rodrilang.librarymanager.admin.catalog.importer.repository;

import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CatalogImportJobRepository extends JpaRepository<CatalogImportJob, Long> {
    @EntityGraph(attributePaths = {"provider", "format"})
    Page<CatalogImportJob> findAllByOrderByIdDesc(Pageable pageable);
    @EntityGraph(attributePaths = {"provider", "format"})
    @Query("select j from CatalogImportJob j where j.id = :id")
    Optional<CatalogImportJob> findDetailedById(@Param("id") Long id);
}
