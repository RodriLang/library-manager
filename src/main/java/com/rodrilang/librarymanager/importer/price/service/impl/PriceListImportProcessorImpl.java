package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.ImportStatistics;
import com.rodrilang.librarymanager.importer.price.dto.PriceListBatchResult;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.PriceListValidationResult;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListImportParserResolver;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportBatchService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportJobProgressService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportProcessor;
import com.rodrilang.librarymanager.importer.price.service.PriceListRowDeduplicator;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportErrorValidator;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportSafetyValidator;
import com.rodrilang.librarymanager.importer.price.validator.PriceListValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListImportProcessorImpl implements PriceListImportProcessor {

    @Value("${app.price-import.batch-size:500}")
    private int batchSize;

    private final PriceListImportJobRepository jobRepository;
    private final PriceListImportParserResolver importParserResolver;
    private final PriceListImportSafetyValidator safetyValidator;
    private final PriceListImportErrorValidator importErrorValidator;
    private final PriceListValidationService validationService;
    private final PriceListImportJobProgressService progressService;
    private final PriceListRowDeduplicator rowDeduplicator;
    private final PriceListImportBatchService batchService;

    @Override
    public void process(Long jobId, byte[] fileBytes) {
        try {
            progressService.markProcessing(jobId);

            PriceListImportJob job = jobRepository.findWithImportConfigById(jobId)
                    .orElseThrow(() -> new BusinessException("No se encontró el trabajo de importación."));

            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
                List<PriceListRow> parsedRows = importParserResolver.parse(workbook, job);
                List<PriceListRow> rows = rowDeduplicator.deduplicate(parsedRows);

                log.info(
                        "Price list deduplication completed. jobId={} parsedRows={} effectiveRows={} duplicates={}",
                        jobId,
                        parsedRows.size(),
                        rows.size(),
                        parsedRows.size() - rows.size()
                );

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
        List<PriceListRow> rows = validation.validRows();

        int processedRows = 0;
        int createdBooks = 0;
        int createdPrices = 0;
        int updatedPrices = 0;
        int unchangedPrices = 0;

        for (int fromIndex = 0; fromIndex < rows.size(); fromIndex += batchSize) {
            int toIndex = Math.min(fromIndex + batchSize, rows.size());
            List<PriceListRow> batchRows = rows.subList(fromIndex, toIndex);

            PriceListBatchResult result = batchService.processBatch(batchRows, jobId);

            processedRows += result.processedRows();
            createdBooks += result.createdBooks();
            createdPrices += result.createdPrices();
            updatedPrices += result.updatedPrices();
            unchangedPrices += result.unchangedPrices();

            progressService.updateProgress(jobId, new ImportStatistics(
                    processedRows,
                    createdBooks,
                    createdPrices,
                    updatedPrices,
                    unchangedPrices,
                    validation.errors().size()
            ));

            log.info(
                    "Price import batch completed. jobId={} processedRows={}/{} createdBooks={} createdPrices={} updatedPrices={} unchangedPrices={}",
                    jobId,
                    processedRows,
                    rows.size(),
                    createdBooks,
                    createdPrices,
                    updatedPrices,
                    unchangedPrices
            );
        }

        progressService.markCompleted(jobId, new ImportStatistics(
                processedRows,
                createdBooks,
                createdPrices,
                updatedPrices,
                unchangedPrices,
                validation.errors().size()
        ));
    }
}