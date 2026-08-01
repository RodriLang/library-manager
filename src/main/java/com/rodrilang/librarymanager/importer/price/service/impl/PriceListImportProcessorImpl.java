package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.dto.internal.BookImportResult;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.ImportStatistics;
import com.rodrilang.librarymanager.importer.price.dto.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.PriceListValidationResult;
import com.rodrilang.librarymanager.importer.price.factory.ImportContextFactory;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListImportParserResolver;
import com.rodrilang.librarymanager.importer.price.service.PriceListBookUpsertService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportJobProgressService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportProcessor;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportErrorValidator;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportSafetyValidator;
import com.rodrilang.librarymanager.importer.price.validator.PriceListValidationService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListImportProcessorImpl implements PriceListImportProcessor {

    @Value("${app.price-import.progress-update-every-rows:200}")
    private int progressUpdateEveryRows;

    private final PriceListImportJobRepository jobRepository;
    private final PriceListImportParserResolver importParserResolver;
    private final BookRepository bookRepository;
    private final PriceListImportSafetyValidator safetyValidator;
    private final PriceListImportErrorValidator importErrorValidator;
    private final PriceListBookUpsertService bookUpsertService;
    private final PriceListValidationService validationService;
    private final ImportContextFactory importContextFactory;
    private final PriceListImportJobProgressService progressService;
    private final EditorialPriceService editorialPriceService;

    @Transactional
    @Override
    public void process(Long jobId, byte[] fileBytes) {
        try {
            progressService.markProcessing(jobId);

            PriceListImportJob job = jobRepository.findWithImportConfigById(jobId)
                    .orElseThrow(() -> new BusinessException("No se encontró el trabajo de importación."));

            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
                List<PriceListRow> rows = importParserResolver.parse(workbook, job);
                PriceListValidationResult validation = validationService.validate(rows);

                progressService.updateTotalRows(jobId, validation.validRows().size(), validation.errors().size());
                progressService.saveErrors(jobId, validation.errors());
                safetyValidator.validate(rows, validation.validRows());

                validation.errors().forEach(error -> log.warn(
                        "Import error row={} isbn={} severity={} message={}",
                        error.rowNumber(),
                        error.isbn(),
                        error.severity(),
                        error.message()
                ));

                importErrorValidator.validate(validation.errors());
                processValidRows(jobId, validation, job);
            }
        } catch (Exception ex) {
            log.error("Price list import failed. jobId={}", jobId, ex);
            progressService.markFailed(jobId, ex.getMessage());

            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }

            throw new BusinessException("No se pudo procesar la lista de precios: " + ex.getMessage());
        }
    }

    private void processValidRows(
            Long jobId,
            PriceListValidationResult validation,
            PriceListImportJob job
    ) {
        ImportContext context = importContextFactory.create(validation.validRows());

        int createdBooks = 0;
        int createdPrices = 0;
        int updatedPrices = 0;
        int unchangedPrices = 0;
        int processedRows = 0;

        List<Book> newBooksToSave = new ArrayList<>();
        List<PriceListRow> newBookRows = new ArrayList<>();

        List<Book> priceBooks = new ArrayList<>();
        List<PriceListRow> priceRows = new ArrayList<>();

        for (PriceListRow row : validation.validRows()) {
            BookImportResult result = bookUpsertService.findOrCreate(row, context);

            if (result.created()) {
                newBooksToSave.add(result.book());
                newBookRows.add(row);
                createdBooks++;
            } else {
                priceBooks.add(result.book());
                priceRows.add(row);
            }

            processedRows++;

            if (processedRows % progressUpdateEveryRows == 0) {
                PriceImportCounters counters = flushImportBatch(
                        newBooksToSave,
                        newBookRows,
                        priceBooks,
                        priceRows,
                        job
                );

                createdPrices += counters.createdPrices();
                updatedPrices += counters.updatedPrices();
                unchangedPrices += counters.unchangedPrices();

                progressService.updateProgress(jobId, new ImportStatistics(
                        processedRows,
                        createdBooks,
                        createdPrices,
                        updatedPrices,
                        unchangedPrices,
                        validation.errors().size()
                ));
            }
        }

        PriceImportCounters counters = flushImportBatch(
                newBooksToSave,
                newBookRows,
                priceBooks,
                priceRows,
                job
        );

        createdPrices += counters.createdPrices();
        updatedPrices += counters.updatedPrices();
        unchangedPrices += counters.unchangedPrices();

        progressService.markCompleted(jobId, new ImportStatistics(
                processedRows,
                createdBooks,
                createdPrices,
                updatedPrices,
                unchangedPrices,
                validation.errors().size()
        ));
    }

    private PriceImportCounters flushImportBatch(
            List<Book> newBooksToSave,
            List<PriceListRow> newBookRows,
            List<Book> priceBooks,
            List<PriceListRow> priceRows,
            PriceListImportJob job
    ) {
        if (!newBooksToSave.isEmpty()) {
            List<Book> savedBooks = bookRepository.saveAll(newBooksToSave);

            priceBooks.addAll(savedBooks);
            priceRows.addAll(newBookRows);

            newBooksToSave.clear();
            newBookRows.clear();
        }

        if (priceBooks.isEmpty()) {
            return new PriceImportCounters(0, 0, 0);
        }

        PriceImportCounters counters =
                editorialPriceService.registerOrUpdateBatchForImport(
                        priceBooks,
                        priceRows,
                        job
                );

        priceBooks.clear();
        priceRows.clear();

        return counters;
    }
}