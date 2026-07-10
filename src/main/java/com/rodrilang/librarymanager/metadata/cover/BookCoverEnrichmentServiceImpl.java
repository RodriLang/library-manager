package com.rodrilang.librarymanager.metadata.cover;

import com.rodrilang.librarymanager.enums.CoverSearchStatus;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.metadata.cover.job.dto.CoverEnrichmentBatchResult;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCoverEnrichmentServiceImpl implements BookCoverEnrichmentService {

    private final BookRepository bookRepository;
    private final CoverSearchService coverSearchService;

    @Override
    @Transactional
    public CoverEnrichmentBatchResult enrichNextBatch(int batchSize) {
        List<Long> ids = bookRepository.findPendingCoverEnrichmentIds(
                PageRequest.of(0, batchSize)
        );

        if (ids.isEmpty()) {
            return new CoverEnrichmentBatchResult(0, 0, 0, 0);
        }

        List<Book> books = bookRepository.findBooksWithCoverDataByIdIn(ids);
        books.sort(Comparator.comparing(Book::getId));

        int processed = 0;
        int found = 0;
        int notFound = 0;
        int errors = 0;

        for (Book book : books) {
            try {
                boolean updated = enrichCover(book);

                processed++;

                if (updated) {
                    found++;
                } else {
                    notFound++;
                }

            } catch (Exception ex) {
                errors++;
                processed++;

                book.setCoverSearchStatus(CoverSearchStatus.ERROR);
                book.setCoverCheckedAt(Instant.now());

                log.error(
                        "Error buscando portada para bookId={} isbn={}",
                        book.getId(),
                        book.getIsbn(),
                        ex
                );
            }
        }

        return new CoverEnrichmentBatchResult(processed, found, notFound, errors);
    }

    @Override
    @Transactional
    public boolean enrichBookCover(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Libro no encontrado"));

        return enrichCover(book);
    }

    private boolean enrichCover(Book book) {
        if (hasText(book.getCoverUrl())) {
            return false;
        }

        book.setCoverSearchAttempts(
                book.getCoverSearchAttempts() == null
                        ? 1
                        : book.getCoverSearchAttempts() + 1
        );
        book.setCoverCheckedAt(Instant.now());

        return coverSearchService.findCover(book)
                .map(candidate -> {
                    book.setCoverUrl(candidate.url());
                    book.setCoverSource(candidate.source());
                    book.setCoverSearchStatus(CoverSearchStatus.FOUND);

                    log.info(
                            "Portada encontrada para bookId={} isbn={} source={} score={}",
                            book.getId(),
                            book.getIsbn(),
                            candidate.source(),
                            candidate.score()
                    );

                    return true;
                })
                .orElseGet(() -> {
                    book.setCoverSearchStatus(CoverSearchStatus.NOT_FOUND);

                    log.info(
                            "No se encontró portada para bookId={} isbn={} title={}",
                            book.getId(),
                            book.getIsbn(),
                            book.getTitle()
                    );

                    return false;
                });
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}