package com.rodrilang.librarymanager.metadata.cover.job;

import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.metadata.cover.BookCoverEnrichmentService;
import com.rodrilang.librarymanager.metadata.cover.job.dto.CoverEnrichmentBatchResult;
import com.rodrilang.librarymanager.metadata.cover.job.dto.CoverEnrichmentJobStatusResponse;
import com.rodrilang.librarymanager.metadata.cover.job.model.CoverEnrichmentJob;
import com.rodrilang.librarymanager.metadata.cover.job.model.CoverEnrichmentJobStatus;
import com.rodrilang.librarymanager.metadata.cover.job.repository.CoverEnrichmentJobRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CoverEnrichmentJobServiceImpl implements CoverEnrichmentJobService {

    private static final int DEFAULT_BATCH_SIZE = 25;

    private final CoverEnrichmentJobRepository jobRepository;
    private final BookRepository bookRepository;
    private final BookCoverEnrichmentService bookCoverEnrichmentService;
    private final CoverEnrichmentAsyncRunner asyncRunner;

    @Override
    public CoverEnrichmentJobStatusResponse startJob(int batchSize) {
        long totalBooks = bookRepository.countBooksPendingCoverEnrichment();

        CoverEnrichmentJob job = CoverEnrichmentJob.builder()
                .status(CoverEnrichmentJobStatus.PENDING)
                .totalBooks((int) totalBooks)
                .processedBooks(0)
                .foundCovers(0)
                .notFoundCovers(0)
                .errorCount(0)
                .progressPercentage(totalBooks == 0 ? 100 : 0)
                .build();

        jobRepository.save(job);

        asyncRunner.run(job.getId(), batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize);
        return toResponse(job);
    }

    @Override
    public CoverEnrichmentJobStatusResponse getStatus(Long jobId) {
        CoverEnrichmentJob job = getJob(jobId);
        return toResponse(job);
    }

    @Async
    public void runAsync(Long jobId, int batchSize) {
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

    private int calculateProgress(CoverEnrichmentJob job) {
        if (job.getTotalBooks() == 0) {
            return 100;
        }

        return Math.min(
                100,
                (int) Math.round((job.getProcessedBooks() * 100.0) / job.getTotalBooks())
        );
    }

    private CoverEnrichmentJob getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job de portadas no encontrado"));
    }

    private CoverEnrichmentJobStatusResponse toResponse(CoverEnrichmentJob job) {
        return new CoverEnrichmentJobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getTotalBooks(),
                job.getProcessedBooks(),
                job.getFoundCovers(),
                job.getNotFoundCovers(),
                job.getErrorCount(),
                job.getProgressPercentage(),
                job.getErrorMessage()
        );
    }
}