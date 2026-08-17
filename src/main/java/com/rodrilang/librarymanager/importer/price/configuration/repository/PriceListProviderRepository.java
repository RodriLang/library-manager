package com.rodrilang.librarymanager.importer.price.configuration.repository;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceListProviderRepository extends JpaRepository<PriceListProvider, Long> {

    List<PriceListProvider> findAllByActiveTrueOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);
}