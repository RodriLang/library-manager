package com.rodrilang.librarymanager.metadata.cover.job.repository;

import com.rodrilang.librarymanager.metadata.cover.job.model.CoverEnrichmentJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverEnrichmentJobRepository extends JpaRepository<CoverEnrichmentJob, Long> {
}