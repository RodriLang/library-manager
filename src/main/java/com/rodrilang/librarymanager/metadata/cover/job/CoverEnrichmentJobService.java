package com.rodrilang.librarymanager.metadata.cover.job;


import com.rodrilang.librarymanager.metadata.cover.job.dto.CoverEnrichmentJobStatusResponse;

public interface CoverEnrichmentJobService {

    CoverEnrichmentJobStatusResponse startJob(int batchSize);

    CoverEnrichmentJobStatusResponse getStatus(Long jobId);
}