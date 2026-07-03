package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.PriceListValidationResult;
import com.rodrilang.librarymanager.importer.price.factory.ImportContextFactory;
import com.rodrilang.librarymanager.importer.price.parser.PriceListParser;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.importer.price.service.PriceListBookUpsertService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportJobProgressService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportProcessor;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportErrorValidator;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportSafetyValidator;
import com.rodrilang.librarymanager.importer.price.validator.PriceListValidationService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListImportProcessorImpl implements PriceListImportProcessor {

    private static final int PROGRESS_UPDATE_EVERY_ROWS = 50;

    private final List<PriceListParser> parsers;
    private final BookRepository bookRepository;
    private final PriceListImportSafetyValidator safetyValidator;
    private final PriceListImportErrorValidator importErrorValidator;
    private final PriceListBookUpsertService bookUpsertService;
    private final PriceListValidationService validationService;
    private final ImportContextFactory importContextFactory;
    private final PriceListImportJobProgressService progressService;

    @Transactional
    @Override
    public void process(
            Long jobId,
            PriceListSource priceListSource,
            byte[] fileBytes
    ) {
        try {
            progressService.markProcessing(jobId);

            PriceListParser parser = resolveParser(priceListSource);

            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
                parser.validateTemplate(workbook);

                List<PriceListRow> rows = parser.parse(workbook);

                PriceListValidationResult validation = validationService.validate(rows);

                progressService.updateTotalRows(
                        jobId,
                        validation.validRows().size(),
                        validation.errors().size()
                );

                progressService.saveErrors(jobId, validation.errors());

                safetyValidator.validate(rows, validation.validRows());

                validation.errors().forEach(error ->
                        log.warn(
                                "Import error row={} isbn={} severity={} message={}",
                                error.rowNumber(),
                                error.isbn(),
                                error.severity(),
                                error.message()
                        )
                );

                importErrorValidator.validate(validation.errors());

                processValidRows(jobId, validation);

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
            PriceListValidationResult validation
    ) {
        ImportContext context = importContextFactory.create(validation.validRows());

        int createdBooks = 0;
        int updatedBooks = 0;
        int processedRows = 0;

        List<Book> booksToSave = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        for (PriceListRow row : validation.validRows()) {
            boolean existed = bookUpsertService.exists(row, context);

            Book book = bookUpsertService.upsert(row, context, today);
            booksToSave.add(book);

            if (existed) {
                updatedBooks++;
            } else {
                createdBooks++;
            }

            processedRows++;

            if (processedRows % PROGRESS_UPDATE_EVERY_ROWS == 0) {
                bookRepository.saveAll(booksToSave);
                booksToSave.clear();

                progressService.updateProgress(
                        jobId,
                        processedRows,
                        createdBooks,
                        updatedBooks
                );
            }
        }

        if (!booksToSave.isEmpty()) {
            bookRepository.saveAll(booksToSave);
        }

        progressService.markCompleted(
                jobId,
                processedRows,
                createdBooks,
                updatedBooks,
                validation.errors().size()
        );
    }

    private PriceListParser resolveParser(PriceListSource priceListSource) {
        return parsers.stream()
                .filter(parser -> parser.supports(priceListSource))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "No existe un parser configurado para la lista: " + priceListSource
                ));
    }
}