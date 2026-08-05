package com.rodrilang.librarymanager.importer.price.staging;

import com.rodrilang.librarymanager.importer.price.config.PriceListImportProperties;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.configuration.parser.ConfigurablePriceListRowInspector;
import com.rodrilang.librarymanager.importer.price.configuration.parser.StreamingConfigurablePriceListParser;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListStagingStatistics;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListValidationResult;
import com.rodrilang.librarymanager.importer.price.dto.internal.StagingInsertResult;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportStagingRepository;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportJobProgressService;
import com.rodrilang.librarymanager.importer.price.validator.PriceListValidationService;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListImportStagingService {

    private static final BigDecimal MAX_REASONABLE_PRICE = new BigDecimal("500000");
    private static final int MAX_INVALID_ISBN_LOGS = 20;

    private final AtomicInteger invalidIsbnLogCount = new AtomicInteger();

    private final PriceListImportStagingRepository repository;
    private final PriceListValidationService validationService;
    private final ConfigurablePriceListRowInspector rowInspector;
    private final PriceListImportProperties properties;
    private final IsbnService isbnService;
    private final PriceListImportJobProgressService progressService;

    public PriceListStagingStatistics stage(
            Long jobId,
            Path filePath,
            PriceListImportConfig config,
            StreamingConfigurablePriceListParser parser
    ) {
        List<PriceListRow> buffer =
                new ArrayList<>(properties.stagingBatchSize());

        MutableStagingStatistics statistics =
                new MutableStagingStatistics();

        parser.parse(
                filePath,
                config,
                row -> {
                    statistics.incrementParsed();

                    if (rowInspector.shouldSkip(row)) {
                        return;
                    }

                    statistics.incrementProcessable();
                    inspectRow(row, statistics);

                    buffer.add(row);

                    if (buffer.size() >= properties.stagingBatchSize()) {
                        flush(jobId, buffer, statistics);
                    }
                }
        );

        flush(jobId, buffer, statistics);

        log.info("""
                        Staging statistics:
                        parsed={}
                        processable={}
                        valid={}
                        invalid={}
                        duplicated={}
                        """,
                statistics.toImmutable().parsedRows(),
                statistics.toImmutable().processableRows(),
                statistics.toImmutable().validRows(),
                statistics.toImmutable().invalidRows(),
                statistics.toImmutable().duplicateRows()
        );

        return statistics.toImmutable();
    }

    private void inspectRow(
            PriceListRow row,
            MutableStagingStatistics statistics
    ) {
        if (row.retailPrice() != null) {
            statistics.incrementRowsWithPrice();

            if (row.retailPrice().compareTo(MAX_REASONABLE_PRICE) > 0) {
                statistics.incrementRowsWithAbsurdPrice();
            }
        }

        if (hasText(row.title())) {
            statistics.incrementRowsWithTitle();
        }

        if (hasText(row.isbn())) {
            statistics.incrementRowsWithIsbn();

            ParsedIsbn parsedIsbn =
                    isbnService.parse(row.isbn());

            if (parsedIsbn.valid()) {
                statistics.incrementRowsWithValidIsbn();
            } else if (
                    invalidIsbnLogCount.getAndIncrement()
                            < MAX_INVALID_ISBN_LOGS
            ) {
                log.debug(
                        "Invalid ISBN sample. row={} isbn={}",
                        row.rowNumber(),
                        row.isbn()
                );
            }

            if (parsedIsbn.valid()) {
                statistics.incrementRowsWithValidIsbn();
            }
        }
    }

    private void flush(
            Long jobId,
            List<PriceListRow> buffer,
            MutableStagingStatistics statistics
    ) {
        if (buffer.isEmpty()) {
            return;
        }

        List<PriceListRow> rows = List.copyOf(buffer);
        buffer.clear();

        PriceListValidationResult validation =
                validationService.validate(rows);

        if (!validation.errors().isEmpty()) {
            progressService.saveErrors(
                    jobId,
                    validation.errors()
            );
        }

        StagingInsertResult result =
                repository.insertBatch(
                        jobId,
                        validation.validRows()
                );

        statistics.addValid(result.insertedRows());
        statistics.addDuplicates(result.duplicatedRows());

        long invalidRows = countInvalidRows(
                rows,
                validation.validRows()
        );

        long classifiedValidRows = result.insertedRows() + (long) result.duplicatedRows();

        if (classifiedValidRows != validation.validRows().size()) {
            throw new IllegalStateException(
                    "El resultado del staging no coincide con las filas válidas. "
                            + "expected=" + validation.validRows().size()
                            + ", actual=" + classifiedValidRows
            );
        }

        statistics.addInvalid(invalidRows);
    }

    private long countInvalidRows(
            List<PriceListRow> rows,
            List<PriceListRow> validRows
    ) {
        return Math.max(
                0,
                rows.size() - validRows.size()
        );
    }
}