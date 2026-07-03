package com.rodrilang.librarymanager.importer.price.repository;

import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceListImportJobErrorRepository extends JpaRepository<PriceListImportJobError, Long> {

    List<PriceListImportJobError> findByJobIdOrderByRowNumberAsc(Long jobId);
}