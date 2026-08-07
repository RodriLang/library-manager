package com.rodrilang.librarymanager.cover.job.service;

import com.rodrilang.librarymanager.cover.job.configuration.BookCoverJobProperties;
import com.rodrilang.librarymanager.cover.job.entity.BookCoverJob;
import com.rodrilang.librarymanager.cover.job.repository.BookCoverJobRepository;
import com.rodrilang.librarymanager.cover.job.request.CreateBookCoverJobRequest;
import com.rodrilang.librarymanager.cover.job.response.CreateBookCoverJobResult;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.media.download.RemoteImageUrlNormalizer;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookCoverJobService {

    private final BookRepository bookRepository;
    private final BookCoverJobRepository jobRepository;
    private final PriceListImportJobRepository priceListImportJobRepository;

    private final RemoteImageUrlNormalizer urlNormalizer;
    private final BookCoverJobKeyService jobKeyService;
    private final BookCoverJobProperties properties;

    @Transactional
    public CreateBookCoverJobResult create(
            CreateBookCoverJobRequest request
    ) {
        validateRequest(request);

        String normalizedUrl = urlNormalizer.normalize(
                request.sourceUrl()
        );

        String jobKey = jobKeyService.generate(
                request.bookId(),
                normalizedUrl
        );

        return jobRepository
                .findByJobKey(jobKey)
                .map(existing ->
                        CreateBookCoverJobResult.duplicate(
                                existing.getId()
                        )
                )
                .orElseGet(() ->
                        persistNewJob(
                                request,
                                normalizedUrl,
                                jobKey
                        )
                );
    }

    private CreateBookCoverJobResult persistNewJob(
            CreateBookCoverJobRequest request,
            String normalizedUrl,
            String jobKey
    ) {
        Book book = bookRepository
                .findById(request.bookId())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "No se encontró el libro con id "
                                        + request.bookId()
                        )
                );

        PriceListImportJob priceListImportJob =
                findPriceListImportJob(
                        request.priceListImportJobId()
                );

        BookCoverJob job = BookCoverJob.create(
                book,
                priceListImportJob,
                request.sourceUrl(),
                normalizedUrl,
                request.source(),
                request.sourceRowNumber(),
                jobKey,
                properties.maxAttempts()
        );

        try {
            BookCoverJob saved = jobRepository.saveAndFlush(job);

            return CreateBookCoverJobResult.created(
                    saved.getId()
            );
        } catch (DataIntegrityViolationException exception) {
            return jobRepository
                    .findByJobKey(jobKey)
                    .map(existing ->
                            CreateBookCoverJobResult.duplicate(
                                    existing.getId()
                            )
                    )
                    .orElseThrow(() -> exception);
        }
    }

    private void validateRequest(
            CreateBookCoverJobRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "La solicitud es obligatoria"
            );
        }

        if (request.bookId() == null) {
            throw new IllegalArgumentException(
                    "El id del libro es obligatorio"
            );
        }

        if (
                request.sourceUrl() == null
                        || request.sourceUrl().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "La URL de portada es obligatoria"
            );
        }

        if (request.source() == null) {
            throw new IllegalArgumentException(
                    "El origen de la portada es obligatorio"
            );
        }
    }

    private PriceListImportJob findPriceListImportJob(
            Long priceListImportJobId
    ) {
        if (priceListImportJobId == null) {
            return null;
        }

        return priceListImportJobRepository
                .findById(priceListImportJobId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "No se encontró la importación de precios con id "
                                        + priceListImportJobId
                        )
                );
    }
}