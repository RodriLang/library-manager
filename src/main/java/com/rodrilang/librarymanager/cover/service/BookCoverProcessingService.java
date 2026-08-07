package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.configuration.BookCoverProcessingProperties;
import com.rodrilang.librarymanager.cover.dto.BookCoverProcessingResult;
import com.rodrilang.librarymanager.cover.repository.BookCoverCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCoverProcessingService {

    private final BookCoverProcessingProperties properties;
    private final BookCoverCandidateRepository candidateRepository;
    private final BookCoverCandidateProcessor processor;
    private final BookCoverCandidateStateService stateService;
    private final ExecutorService bookCoverExecutor;

    public BookCoverProcessingResult processNextBatch() {
        recoverTimedOutCandidates();

        List<Long> candidateIds =
                candidateRepository.claimPendingCandidateIds(
                        properties.batchSize(),
                        Instant.now()
                );

        List<CompletableFuture<Boolean>> futures =
                candidateIds.stream()
                        .map(bookId ->
                                CompletableFuture.supplyAsync(
                                        () -> processSafely(bookId),
                                        bookCoverExecutor
                                )
                        )
                        .toList();

        int completed = 0;
        int failed = 0;

        for (CompletableFuture<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.join())) {
                completed++;
            } else {
                failed++;
            }
        }

        return new BookCoverProcessingResult(
                candidateIds.size(),
                completed,
                failed
        );
    }

    private void recoverTimedOutCandidates() {
        Instant startedBefore = Instant.now()
                .minus(properties.processingTimeout());

        int recovered =
                candidateRepository
                        .recoverTimedOutCandidates(startedBefore);

        if (recovered > 0) {
            log.warn(
                    "Se recuperaron {} portadas candidatas bloqueadas",
                    recovered
            );
        }
    }

    private boolean processSafely(Long bookId) {
        try {
            processor.process(bookId);
            return true;

        } catch (RuntimeException exception) {
            log.error(
                    "Error no controlado procesando portada del libro {}",
                    bookId,
                    exception
            );

            try {
                stateService.fail(
                        bookId,
                        "Error inesperado procesando la portada."
                );
            } catch (RuntimeException stateException) {
                log.error(
                        "No se pudo actualizar el estado del candidato del libro {}",
                        bookId,
                        stateException
                );
            }

            return false;
        }
    }
}