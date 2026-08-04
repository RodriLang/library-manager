package com.rodrilang.librarymanager.importer.price.configuration.repository;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceListProviderRepository extends JpaRepository<PriceListProvider, Long> {

    Optional<PriceListProvider> findByCodeIgnoreCase(String code);

    List<PriceListProvider> findAllByActiveTrueOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);
}