package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.dto.internal.BookImportResult;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.service.ProviderBookService;
import com.rodrilang.librarymanager.importer.price.dto.internal.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListBatchResult;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListResolvedPriceCandidate;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListStagingRow;
import com.rodrilang.librarymanager.importer.price.factory.ImportContextFactory;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportPriceStagingRepository;
import com.rodrilang.librarymanager.importer.price.service.PriceListBookUpsertService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportBatchService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListImportBatchServiceImpl implements PriceListImportBatchService {

    private final BookRepository bookRepository;
    private final ImportContextFactory importContextFactory;
    private final PriceListImportJobRepository jobRepository;
    private final PriceListBookUpsertService bookUpsertService;
    private final ProviderBookService providerBookService;
    private final PriceListImportPriceStagingRepository priceStagingRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PriceListBatchResult processBatch(
            List<PriceListStagingRow> stagingRows,
            Long jobId
    ) {
        if (stagingRows == null || stagingRows.isEmpty()) {
            return new PriceListBatchResult(
                    0,
                    0,
                    0
            );
        }

        long batchStartedAt = System.nanoTime();

        long stepStartedAt = System.nanoTime();

        PriceListImportJob job = jobRepository.findWithImportConfigById(jobId)
                .orElseThrow(() -> new BusinessException("No se encontró el trabajo de importación."));

        long jobLoadMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        List<PriceListRow> rows = stagingRows.stream()
                .map(PriceListStagingRow::row)
                .toList();

        long rowsPreparationMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        ImportContext context = importContextFactory.create(rows, job.getProvider());

        long contextMs = elapsedMillis(stepStartedAt);

        List<Book> books = new ArrayList<>(rows.size());

        List<Book> newBooks = new ArrayList<>();

        List<Integer> newBookIndexes = new ArrayList<>();

        int createdBooks = 0;

        stepStartedAt = System.nanoTime();

        for (int i = 0; i < rows.size(); i++) {
            PriceListRow row = rows.get(i);

            BookImportResult result = bookUpsertService.findOrCreate(row, context);

            books.add(result.book());

            if (result.created()) {
                newBooks.add(result.book());
                newBookIndexes.add(i);
                createdBooks++;
            }
        }

        long bookResolutionMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        if (!newBooks.isEmpty()) {
            List<Book> savedBooks = bookRepository.saveAll(newBooks);

            for (int i = 0; i < savedBooks.size(); i++) {
                books.set(newBookIndexes.get(i), savedBooks.get(i));
            }
        }

        long newBooksPersistenceMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        providerBookService.registerBatch(job.getProvider(), books, rows);

        long providerBooksMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        List<PriceListResolvedPriceCandidate> priceCandidates = getPriceListResolvedPriceCandidates(rows, books);

        long priceCandidatePreparationMs =
                elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        int stagedBooks =
                priceStagingRepository.registerBatch(
                        jobId,
                        priceCandidates
                );

        long priceStagingMs = elapsedMillis(stepStartedAt);

        /*
         * Importante para el benchmark:
         *
         * findOrCreate() puede modificar Books administrados por JPA.
         * Sin este flush, esos UPDATE podrían ejecutarse recién al commit
         * y quedar afuera de los tiempos internos.
         *
         * Como esta transacción REQUIRES_NEW termina inmediatamente después,
         * el flush iba a ocurrir igualmente antes del commit.
         */
        stepStartedAt = System.nanoTime();

        entityManager.flush();

        long jpaFlushMs = elapsedMillis(stepStartedAt);

        long totalMs = elapsedMillis(batchStartedAt);

        long measuredMs =
                jobLoadMs
                        + rowsPreparationMs
                        + contextMs
                        + bookResolutionMs
                        + newBooksPersistenceMs
                        + providerBooksMs
                        + priceCandidatePreparationMs
                        + priceStagingMs
                        + jpaFlushMs;

        long otherMs = Math.max(0L, totalMs - measuredMs);

        log.info(
                "Price list book batch timing. "
                        + "jobId={} "
                        + "rows={} "
                        + "createdBooks={} "
                        + "stagedBooks={} "
                        + "jobLoad={}ms "
                        + "rowsPreparation={}ms "
                        + "context={}ms "
                        + "bookResolution={}ms "
                        + "newBooksPersistence={}ms "
                        + "providerBooks={}ms "
                        + "priceCandidates={}ms "
                        + "priceStaging={}ms "
                        + "jpaFlush={}ms "
                        + "other={}ms "
                        + "total={}ms",
                jobId,
                stagingRows.size(),
                createdBooks,
                stagedBooks,
                jobLoadMs,
                rowsPreparationMs,
                contextMs,
                bookResolutionMs,
                newBooksPersistenceMs,
                providerBooksMs,
                priceCandidatePreparationMs,
                priceStagingMs,
                jpaFlushMs,
                otherMs,
                totalMs
        );

        return new PriceListBatchResult(
                stagingRows.size(),
                createdBooks,
                stagedBooks
        );
    }

    private static @NonNull List<PriceListResolvedPriceCandidate> getPriceListResolvedPriceCandidates(List<PriceListRow> rows, List<Book> books) {
        List<PriceListResolvedPriceCandidate> priceCandidates = new ArrayList<>(rows.size());

        for (int i = 0; i < rows.size(); i++) {
            Book book = books.get(i);
            PriceListRow row = rows.get(i);

            if (book == null
                    || book.getId() == null
                    || row.retailPrice() == null) {
                continue;
            }

            priceCandidates.add(
                    new PriceListResolvedPriceCandidate(
                            book.getId(),
                            row.rowNumber(),
                            row.isbn(),
                            row.retailPrice()
                    )
            );
        }
        return priceCandidates;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}