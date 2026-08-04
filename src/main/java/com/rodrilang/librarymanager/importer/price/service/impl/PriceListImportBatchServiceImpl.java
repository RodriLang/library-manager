package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.dto.internal.BookImportResult;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.service.ProviderBookService;
import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.PriceListBatchResult;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.factory.ImportContextFactory;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.service.PriceListBookUpsertService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportBatchService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceListImportBatchServiceImpl implements PriceListImportBatchService {

    private final BookRepository bookRepository;
    private final ImportContextFactory importContextFactory;
    private final PriceListImportJobRepository jobRepository;
    private final PriceListBookUpsertService bookUpsertService;
    private final EditorialPriceService editorialPriceService;
    private final ProviderBookService providerBookService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PriceListBatchResult processBatch(List<PriceListRow> rows, Long jobId) {
        PriceListImportJob job = jobRepository.findWithImportConfigById(jobId)
                .orElseThrow(() -> new BusinessException("No se encontró el trabajo de importación."));

        ImportContext context = importContextFactory.create(rows, job.getProvider());

        List<Book> books = new ArrayList<>(rows.size());
        List<Book> newBooks = new ArrayList<>();
        List<Integer> newBookIndexes = new ArrayList<>();

        int createdBooks = 0;

        for (int i = 0; i < rows.size(); i++) {
            BookImportResult result = bookUpsertService.findOrCreate(rows.get(i), context);
            books.add(result.book());

            if (result.created()) {
                newBooks.add(result.book());
                newBookIndexes.add(i);
                createdBooks++;
            }
        }

        if (!newBooks.isEmpty()) {
            List<Book> savedBooks = bookRepository.saveAll(newBooks);

            for (int i = 0; i < savedBooks.size(); i++) {
                books.set(newBookIndexes.get(i), savedBooks.get(i));
            }
        }

        providerBookService.registerBatch(job.getProvider(), books, rows);
        PriceImportCounters counters = editorialPriceService.registerOrUpdateBatchForImport(books, rows, job);

        return new PriceListBatchResult(
                rows.size(),
                createdBooks,
                counters.createdPrices(),
                counters.updatedPrices(),
                counters.unchangedPrices()
        );
    }
}