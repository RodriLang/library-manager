package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.configuration.BookCoverProcessingProperties;
import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.cover.exception.RetryableCoverProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCoverCandidateProcessor {

    private final BookCoverCandidateStateService stateService;
    private final RemoteBookCoverImportService remoteImportService;
    private final BookCoverCandidateLoader candidateLoader;
    private final BookCoverProcessingProperties properties;

    public void process(Long bookId) {
        CandidateContext context = candidateLoader.load(bookId);

        switch (context.decision()) {
            case SKIP_NO_CANDIDATE -> {
                // No hay candidato
            }

            case SKIP_MANUAL_COVER,
                 SKIP_ALREADY_PROCESSED -> stateService.clear(bookId);


            case PROCESS -> processCandidate(context);
        }
    }

    private void processCandidate(CandidateContext context) {
        try {
            remoteImportService.importCover(
                    context.bookId(),
                    context.sourceUrl(),
                    BookCoverSource.PRICE_LIST
            );
        } catch (RetryableCoverProcessingException exception) {
            handleRetryableFailure(
                    context,
                    exception.getMessage()
            );
        } catch (RuntimeException exception) {
            stateService.fail(
                    context.bookId(),
                    safeMessage(exception)
            );

            log.error(
                    "Error definitivo procesando portada candidata del libro {}",
                    context.bookId(),
                    exception
            );
        }
    }

    private void handleRetryableFailure(
            CandidateContext context,
            String error
    ) {
        int attempts = context.attempts() != null
                ? context.attempts()
                : 0;

        if (attempts >= properties.maxAttempts()) {
            stateService.fail(context.bookId(), error);
            return;
        }

        stateService.scheduleRetry(
                context.bookId(),
                error,
                Instant.now().plus(calculateBackoff(attempts))
        );
    }

    private Duration calculateBackoff(int attempts) {
        return switch (attempts) {
            case 0, 1 -> Duration.ofMinutes(5);
            case 2 -> Duration.ofMinutes(30);
            default -> Duration.ofHours(3);
        };
    }

    private String safeMessage(Throwable throwable) {
        if (
                throwable.getMessage() == null
                        || throwable.getMessage().isBlank()
        ) {
            return "No se pudo procesar la portada candidata.";
        }

        return throwable.getMessage();
    }
}