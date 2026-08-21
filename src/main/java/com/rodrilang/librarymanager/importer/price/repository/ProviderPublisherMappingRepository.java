package com.rodrilang.librarymanager.importer.price.repository;

import com.rodrilang.librarymanager.importer.price.model.ProviderPublisherMapping;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProviderPublisherMappingRepository extends JpaRepository<ProviderPublisherMapping, Long> {

    @EntityGraph(attributePaths = "publisher")
    List<ProviderPublisherMapping> findByProviderIdAndExternalNameNormalizedIn(
            Long providerId,
            Collection<String> externalNamesNormalized
    );
}