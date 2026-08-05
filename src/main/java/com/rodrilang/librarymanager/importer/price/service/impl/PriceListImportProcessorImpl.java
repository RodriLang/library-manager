package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.config.PriceListImportProperties;
import com.rodrilang.librarymanager.importer.price.configuration.parser.StreamingConfigurablePriceListParser;
import com.rodrilang.librarymanager.importer.price.dto.internal.ImportStatistics;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListBatchResult;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListImportSafetySummary;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListStagingStatistics;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportStagingRepository;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListImportParserResolver;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportBatchService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportJobProgressService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportProcessor;
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
    private final PriceListImportSafetyValidator safetyValidator;
    private final PriceListImportBatchService batchService;
    private final PriceListImportJobProgressService progressService;
    private final PriceListImportFileStorage fileStorage;
    private final PriceListImportProperties properties;

    @Override
    public void process(Long jobId, Path filePath) {
        try {
            progressService.markProcessing(jobId);

            PriceListImportJob job = jobRepository
                    .findWithImportConfigById(jobId)
                    .orElseThrow(() ->
                            new BusinessException(
                                    "No se encontró el trabajo de importación."
                            )
                    );

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
                    "Staging balance. processableRows={} classifiedRows={}",
                    staging.processableRows(),
                    classifiedRows
            );

            safetyValidator.validate(safetySummary);

            progressService.updateTotalRows(
                    jobId,
                    Math.toIntExact(staging.validRows()),
                    Math.toIntExact(staging.invalidRows())
            );

            processStagedRows(
                    jobId,
                    staging.validRows(),
                    staging.invalidRows()
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
            stagingRepository.deleteByJobId(jobId);
            fileStorage.deleteQuietly(filePath);
        }
    }

    private void processStagedRows(
            Long jobId,
            long totalValidRows,
            long invalidRows
    ) {
        long afterId = 0;

        int processedRows = 0;
        int createdBooks = 0;
        int createdPrices = 0;
        int updatedPrices = 0;
        int unchangedPrices = 0;
        int skippedRows = 0;

        while (true) {
            List<PriceListRow> batch =
                    stagingRepository.findValidBatch(
                            jobId,
                            afterId,
                            properties.batchSize()
                    );

            if (batch.isEmpty()) {
                break;
            }

            long nextAfterId =
                    stagingRepository.findLastIdInBatch(
                            jobId,
                            afterId,
                            properties.batchSize()
                    );

            PriceListBatchResult result =
                    batchService.processBatch(
                            batch,
                            jobId
                    );

            processedRows += result.processedRows();
            createdBooks += result.createdBooks();
            createdPrices += result.createdPrices();
            updatedPrices += result.updatedPrices();
            unchangedPrices += result.unchangedPrices();
            skippedRows += result.skippedRows();

            afterId = nextAfterId;

            ImportStatistics statistics =
                    new ImportStatistics(
                            processedRows,
                            createdBooks,
                            createdPrices,
                            updatedPrices,
                            unchangedPrices,
                            skippedRows,
                            Math.toIntExact(invalidRows)
                    );

            progressService.updateProgress(
                    jobId,
                    statistics
            );

            log.info(
                    "Price import batch completed. "
                            + "jobId={} processedRows={}/{} "
                            + "createdPrices={} updatedPrices={} "
                            + "unchangedPrices={} skippedRows={}",
                    jobId,
                    processedRows,
                    totalValidRows,
                    createdPrices,
                    updatedPrices,
                    unchangedPrices,
                    skippedRows
            );
        }

        ImportStatistics finalStatistics =
                new ImportStatistics(
                        processedRows,
                        createdBooks,
                        createdPrices,
                        updatedPrices,
                        unchangedPrices,
                        skippedRows,
                        Math.toIntExact(invalidRows)
                );

        validateFinalCounters(
                jobId,
                finalStatistics
        );

        progressService.markCompleted(
                jobId,
                finalStatistics
        );
    }

    private void validateFinalCounters(
            Long jobId,
            ImportStatistics statistics
    ) {
        int accountedRows =
                statistics.createdPrices()
                        + statistics.updatedPrices()
                        + statistics.unchangedPrices()
                        + statistics.skippedRows();

        if (accountedRows != statistics.processedRows()) {
            log.warn(
                    "Import counters mismatch. "
                            + "jobId={} processed={} accounted={} "
                            + "difference={} created={} updated={} "
                            + "unchanged={} skipped={}",
                    jobId,
                    statistics.processedRows(),
                    accountedRows,
                    statistics.processedRows() - accountedRows,
                    statistics.createdPrices(),
                    statistics.updatedPrices(),
                    statistics.unchangedPrices(),
                    statistics.skippedRows()
            );
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();

        return message == null || message.isBlank()
                ? "La importación no pudo completarse."
                : message;
    }
}