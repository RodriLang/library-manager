package com.rodrilang.librarymanager.metadata.cover.job;

import com.rodrilang.librarymanager.metadata.cover.BookCoverEnrichmentService;
import com.rodrilang.librarymanager.metadata.cover.job.dto.CoverEnrichmentBatchResult;
import com.rodrilang.librarymanager.metadata.cover.job.model.CoverEnrichmentJob;
import com.rodrilang.librarymanager.metadata.cover.job.model.CoverEnrichmentJobStatus;
import com.rodrilang.librarymanager.metadata.cover.job.repository.CoverEnrichmentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CoverEnrichmentJobProcessor {

    private final CoverEnrichmentJobRepository jobRepository;
    private final BookCoverEnrichmentService bookCoverEnrichmentService;

    public void process(Long jobId, int batchSize) {
        CoverEnrichmentJob job = getJob(jobId);

        try {
            job.setStatus(CoverEnrichmentJobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

            while (job.getProcessedBooks() < job.getTotalBooks()) {
                CoverEnrichmentBatchResult batchResult =
                        bookCoverEnrichmentService.enrichNextBatch(batchSize);

                if (batchResult.processedBooks() == 0) {
                    break;
                }

                job = getJob(jobId);
                job.setProcessedBooks(job.getProcessedBooks() + batchResult.processedBooks());
                job.setFoundCovers(job.getFoundCovers() + batchResult.foundCovers());
                job.setNotFoundCovers(job.getNotFoundCovers() + batchResult.notFoundCovers());
                job.setErrorCount(job.getErrorCount() + batchResult.errorCount());
                job.setProgressPercentage(calculateProgress(job));

                jobRepository.save(job);
            }

            job = getJob(jobId);
            job.setStatus(CoverEnrichmentJobStatus.COMPLETED);
            job.setProgressPercentage(100);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

        } catch (Exception ex) {
            job = getJob(jobId);
            job.setStatus(CoverEnrichmentJobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    private CoverEnrichmentJob getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job de portadas no encontrado"));
    }

    private int calculateProgress(CoverEnrichmentJob job) {
        if (job.getTotalBooks() == 0) {
            return 100;
        }

        return Math.min(
                100,
                (int) Math.round((job.getProcessedBooks() * 100.0) / job.getTotalBooks())
        );
    }
}