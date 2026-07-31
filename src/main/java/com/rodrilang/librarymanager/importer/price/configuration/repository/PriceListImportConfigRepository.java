package com.rodrilang.librarymanager.importer.price.configuration.repository;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceListImportConfigRepository extends JpaRepository<PriceListImportConfig, Long> {

    @EntityGraph(attributePaths = {
            "provider",
            "mappings"
    })
    Optional<PriceListImportConfig> findFirstByProviderIdAndActiveTrue(Long providerId);
}