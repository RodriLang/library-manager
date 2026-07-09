package com.rodrilang.librarymanager.metadata.cover;

import com.rodrilang.librarymanager.metadata.cover.job.dto.CoverEnrichmentBatchResult;

public interface BookCoverEnrichmentService {

    CoverEnrichmentBatchResult enrichNextBatch(int batchSize);

    boolean enrichBookCover(Long bookId);
}