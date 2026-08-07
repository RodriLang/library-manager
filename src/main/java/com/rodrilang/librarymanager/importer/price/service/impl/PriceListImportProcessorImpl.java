package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.config.PriceListImportProperties;
import com.rodrilang.librarymanager.importer.price.configuration.parser.StreamingConfigurablePriceListParser;
import com.rodrilang.librarymanager.importer.price.dto.internal.ImportStatistics;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListBatchResult;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListImportSafetySummary;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListStagingRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListStagingStatistics;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportPhase;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportPriceStagingRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportStagingRepository;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListImportParserResolver;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportBatchService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportJobProgressService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportProcessor;
import com.rodrilang.librarymanager.importer.price.service.PriceListResolvedPriceProcessor;
import com.rodrilang.librarymanager.importer.price.staging.PriceListImportStagingService;
import com.rodrilang.librarymanager.importer.price.storage.PriceListImportFileStorage;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportSafetyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListImportProcessorImpl implements PriceListImportProcessor {

    private final PriceListImportJobRepository jobRepository;
    private final PriceListImportParserResolver parserResolver;
    private final PriceListImportStagingService stagingService;
    private final PriceListImportStagingRepository stagingRepository;
    private final PriceListImportPriceStagingRepository priceStagingRepository;
    private final PriceListImportSafetyValidator safetyValidator;
    private final PriceListImportBatchService batchService;
    private final PriceListResolvedPriceProcessor resolvedPriceProcessor;
    private final PriceListImportJobProgressService progressService;
    private final PriceListImportFileStorage fileStorage;
    private final PriceListImportProperties properties;

    @Override
    public void process(Long jobId, Path filePath) {
        try {
            progressService.markProcessing(jobId);

            PriceListImportJob job = jobRepository
                    .findWithImportConfigById(jobId)
                    .orElseThrow(() -> new BusinessException("No se encontró el trabajo de importación."));

            StreamingConfigurablePriceListParser parser = parserResolver.resolveStreaming(job);

            PriceListStagingStatistics staging =
                    stagingService.stage(
                            jobId,
                            filePath,
                            job.getImportConfig(),
                            parser
                    );

            PriceListImportSafetySummary safetySummary =
                    new PriceListImportSafetySummary(
                            staging.parsedRows(),
                            staging.processableRows(),
                            staging.validRows(),
                            staging.invalidRows(),
                            staging.duplicateRows(),
                            staging.rowsWithPrice(),
                            staging.rowsWithTitle(),
                            staging.rowsWithIsbn(),
                            staging.rowsWithValidIsbn(),
                            staging.rowsWithAbsurdPrice()
                    );

            log.info(
                    """
                            Price list staging completed. jobId={} \
                            parsedRows={} processableRows={} validRows={} invalidRows={} \
                            duplicateRows={} rowsWithPrice={} rowsWithTitle={} \
                            rowsWithIsbn={} rowsWithValidIsbn={} rowsWithAbsurdPrice={}
                            """,
                    jobId,
                    staging.parsedRows(),
                    staging.processableRows(),
                    staging.validRows(),
                    staging.invalidRows(),
                    staging.duplicateRows(),
                    staging.rowsWithPrice(),
                    staging.rowsWithTitle(),
                    staging.rowsWithIsbn(),
                    staging.rowsWithValidIsbn(),
                    staging.rowsWithAbsurdPrice()
            );

            long classifiedRows =
                    staging.validRows()
                            + staging.invalidRows()
                            + staging.duplicateRows();

            log.info(
                    "Staging balance. "
                            + "processableRows={} classifiedRows={}",
                    staging.processableRows(),
                    classifiedRows
            );

            safetyValidator.validate(safetySummary);

            progressService.updateTotalRows(
                    jobId,
                    Math.toIntExact(
                            staging.validRows()
                    ),
                    Math.toIntExact(
                            staging.invalidRows()
                    )
            );

            progressService.updatePhase(
                    jobId,
                    PriceListImportPhase.BOOKS
            );

            BookProcessingStatistics bookStatistics =
                    processBookBatches(
                            jobId,
                            staging.validRows(),
                            staging.invalidRows()
                    );

            log.info(
                    "Book phase completed. "
                            + "jobId={} processedRows={} "
                            + "createdBooks={}",
                    jobId,
                    bookStatistics.processedRows(),
                    bookStatistics.createdBooks()
            );

            progressService.updatePhase(
                    jobId,
                    PriceListImportPhase.PRICES
            );

            PriceImportCounters priceCounters = resolvedPriceProcessor.process(jobId);

            log.info(
                    "Resolved price phase completed. "
                            + "jobId={} createdPrices={} "
                            + "updatedPrices={} "
                            + "unchangedPrices={} "
                            + "skippedRows={}",
                    jobId,
                    priceCounters.createdPrices(),
                    priceCounters.updatedPrices(),
                    priceCounters.unchangedPrices(),
                    priceCounters.skippedRows()
            );

            int duplicateBookRows =
                    bookStatistics.processedRows()
                            - bookStatistics.processedBooks();

            ImportStatistics finalStatistics =
                    new ImportStatistics(
                            bookStatistics.processedRows(),
                            bookStatistics.processedBooks(),
                            duplicateBookRows,
                            bookStatistics.createdBooks(),
                            priceCounters.createdPrices(),
                            priceCounters.updatedPrices(),
                            priceCounters.unchangedPrices(),
                            priceCounters.skippedRows(),
                            Math.toIntExact(staging.invalidRows())
                    );

            validateFinalStatistics(
                    jobId,
                    finalStatistics
            );

            progressService.updateProgress(
                    jobId,
                    finalStatistics
            );

            progressService.markCompleted(
                    jobId,
                    finalStatistics
            );

            log.info(
                    "Price list import completed. "
                            + "jobId={} "
                            + "processedRows={} "
                            + "processedBooks={} "
                            + "createdBooks={} "
                            + "createdPrices={} "
                            + "updatedPrices={} "
                            + "unchangedPrices={} "
                            + "skippedPrices={} "
                            + "invalidRows={}",
                    jobId,
                    finalStatistics.processedRows(),
                    finalStatistics.processedBooks(),
                    finalStatistics.createdBooks(),
                    finalStatistics.createdPrices(),
                    finalStatistics.updatedPrices(),
                    finalStatistics.unchangedPrices(),
                    finalStatistics.skippedPrices(),
                    finalStatistics.errors()
            );

        } catch (Exception exception) {
            log.error(
                    "Price list import failed. jobId={}",
                    jobId,
                    exception
            );

            progressService.markFailed(
                    jobId,
                    safeMessage(exception)
            );

        } finally {

            priceStagingRepository.deleteByJobId(jobId);

            fileStorage.deleteQuietly(filePath);
        }
    }

    private BookProcessingStatistics processBookBatches(
            Long jobId,
            long totalValidRows,
            long invalidRows
    ) {
        long afterId = 0L;

        int processedRows = 0;
        int processedBooks = 0;
        int createdBooks = 0;

        while (true) {
            List<PriceListStagingRow> batch =
                    stagingRepository.findValidBatch(
                            jobId,
                            afterId,
                            properties.batchSize()
                    );

            if (batch.isEmpty()) {
                break;
            }

            PriceListBatchResult result =
                    batchService.processBatch(
                            batch,
                            jobId
                    );

            processedRows +=
                    result.processedRows();

            processedBooks +=
                    result.stagedBooks();

            createdBooks +=
                    result.createdBooks();

            int duplicateBookRows =
                    processedRows - processedBooks;

            afterId =
                    batch.getLast().id();

            ImportStatistics currentStatistics =
                    new ImportStatistics(
                            processedRows,
                            processedBooks,
                            duplicateBookRows,
                            createdBooks,
                            0,
                            0,
                            0,
                            0,
                            Math.toIntExact(
                                    invalidRows
                            )
                    );

            progressService.updateProgress(
                    jobId,
                    currentStatistics
            );

            log.info(
                    "Book import batch completed. "
                            + "jobId={} "
                            + "processedRows={}/{} "
                            + "processedBooks={} "
                            + "createdBooks={}",
                    jobId,
                    processedRows,
                    totalValidRows,
                    processedBooks,
                    createdBooks
            );
        }

        return new BookProcessingStatistics(
                processedRows,
                processedBooks,
                createdBooks
        );
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();

        return message == null || message.isBlank()
                ? "La importación no pudo completarse."
                : message;
    }

    private void validateFinalStatistics(
            Long jobId,
            ImportStatistics statistics
    ) {
        int accountedBooks =
                statistics.createdPrices()
                        + statistics.updatedPrices()
                        + statistics.unchangedPrices()
                        + statistics.skippedPrices();

        if (accountedBooks != statistics.processedBooks()) {
            log.warn(
                    "Price accounting mismatch. "
                            + "jobId={} "
                            + "processedBooks={} "
                            + "accountedBooks={} "
                            + "(created={} updated={} unchanged={} skipped={})",
                    jobId,
                    statistics.processedBooks(),
                    accountedBooks,
                    statistics.createdPrices(),
                    statistics.updatedPrices(),
                    statistics.unchangedPrices(),
                    statistics.skippedPrices()
            );
        }
    }

    private record BookProcessingStatistics(
            int processedRows,
            int processedBooks,
            int createdBooks
    ) {
    }
}