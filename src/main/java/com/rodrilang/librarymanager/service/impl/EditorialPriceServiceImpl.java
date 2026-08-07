package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.internal.EditorialPriceImportResult;
import com.rodrilang.librarymanager.enums.EditorialPriceChangeType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceInsertRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceUpdateRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.EditorialPriceBatchRepository;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EditorialPriceServiceImpl implements EditorialPriceService {

    private final EditorialPriceRepository editorialPriceRepository;
    private final EditorialPriceBatchRepository editorialPriceBatchRepository;

    @Override
    @Transactional
    public EditorialPriceImportResult registerOrUpdateForImport(
            Book book,
            BigDecimal price,
            PriceListProvider provider,
            LocalDate validFrom
    ) {
        EditorialPrice existing = editorialPriceRepository
                .findByBookIdAndProviderIdAndValidFrom(
                        book.getId(),
                        provider.getId(),
                        validFrom
                )
                .orElse(null);

        if (existing == null) {
            EditorialPrice created = EditorialPrice.builder()
                    .book(book)
                    .price(price)
                    .currency("ARS")
                    .provider(provider)
                    .validFrom(validFrom)
                    .active(true)
                    .build();

            return new EditorialPriceImportResult(
                    editorialPriceRepository.save(created),
                    EditorialPriceChangeType.CREATED
            );
        }

        if (existing.getPrice().compareTo(price) == 0) {
            return new EditorialPriceImportResult(
                    existing,
                    EditorialPriceChangeType.UNCHANGED
            );
        }

        existing.setPrice(price);

        return new EditorialPriceImportResult(
                editorialPriceRepository.save(existing),
                EditorialPriceChangeType.UPDATED
        );
    }

    @Override
    @Transactional
    public PriceImportCounters registerOrUpdateBatchForImport(
            List<Book> books,
            List<PriceListRow> rows,
            PriceListImportJob job
    ) {
        if (books.isEmpty()) {
            return new PriceImportCounters(
                    0,
                    0,
                    0,
                    0
            );
        }

        if (books.size() != rows.size()) {
            throw new IllegalArgumentException(
                    "La cantidad de libros no coincide con la cantidad de filas. "
                            + "books=" + books.size()
                            + ", rows=" + rows.size()
            );
        }

        validateOrigin(job);

        List<Long> bookIds = books.stream()
                .map(Book::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        log.info(
                "Loading prices provider={} validFrom={} books={}",
                job.getProvider().getId(),
                job.getValidFrom(),
                bookIds.size()
        );

        log.info(
                "First ids={}",
                bookIds.stream().limit(20).toList()
        );

        long loadStart = System.nanoTime();

        Map<Long, EditorialPrice> existingByBookId =
                loadExistingPrices(bookIds, job)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        price -> price
                                                .getBook()
                                                .getId(),
                                        Function.identity()
                                )
                        );

        log.info(
                "Loaded {} editorial prices in {}ms",
                existingByBookId.size(),
                (System.nanoTime() - loadStart) / 1_000_000
        );

        List<EditorialPriceInsertRow> toInsert =
                new ArrayList<>();

        List<EditorialPriceUpdateRow> toUpdate =
                new ArrayList<>();

        int createdPrices = 0;
        int updatedPrices = 0;
        int unchangedPrices = 0;
        int skippedRows = 0;

        Map<Long, PriceListRow> processedRowsByBookId =
                new HashMap<>();

        long classifyStart = System.nanoTime();

        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);
            PriceListRow row = rows.get(i);

            if (book == null || book.getId() == null) {
                throw new IllegalStateException(
                        "No se puede registrar el precio de un libro sin ID."
                );
            }

            if (row.retailPrice() == null) {
                throw new IllegalStateException(
                        "No se puede registrar un precio nulo. "
                                + "row=" + row.rowNumber()
                                + ", bookId=" + book.getId()
                );
            }

            PriceListRow previousRow =
                    processedRowsByBookId.putIfAbsent(
                            book.getId(),
                            row
                    );

            if (previousRow != null) {
                skippedRows++;

                if (
                        previousRow.retailPrice() != null
                                && previousRow.retailPrice()
                                .compareTo(row.retailPrice()) != 0
                ) {
                    log.warn(
                            "Conflicting prices for same book. "
                                    + "bookId={} title='{}' "
                                    + "firstRow={} firstIsbn={} firstPrice={} "
                                    + "secondRow={} secondIsbn={} secondPrice={}",
                            book.getId(),
                            book.getTitle(),
                            previousRow.rowNumber(),
                            previousRow.isbn(),
                            previousRow.retailPrice(),
                            row.rowNumber(),
                            row.isbn(),
                            row.retailPrice()
                    );
                }

                continue;
            }

            EditorialPrice existing =
                    existingByBookId.get(book.getId());

            if (existing == null) {
                toInsert.add(
                        new EditorialPriceInsertRow(
                                book.getId(),
                                row.retailPrice()
                        )
                );

                createdPrices++;
                continue;
            }

            if (
                    existing.getPrice()
                            .compareTo(row.retailPrice()) == 0
                            && Boolean.TRUE.equals(existing.getActive())
            ) {
                unchangedPrices++;
                continue;
            }

            log.warn(
                    "Editorial price updated during import. "
                            + "editorialPriceId={} bookId={} title='{}' "
                            + "row={} isbn={} previousPrice={} newPrice={} "
                            + "previousActive={} providerId={} validFrom={}",
                    existing.getId(),
                    book.getId(),
                    book.getTitle(),
                    row.rowNumber(),
                    row.isbn(),
                    existing.getPrice(),
                    row.retailPrice(),
                    existing.getActive(),
                    job.getProvider().getId(),
                    job.getValidFrom()
            );

            toUpdate.add(
                    new EditorialPriceUpdateRow(
                            existing.getId(),
                            row.retailPrice()
                    )
            );

            updatedPrices++;
        }

        log.info(
                "Price classification. rows={} inserts={} updates={} unchanged={} skipped={} time={}ms",
                rows.size(),
                toInsert.size(),
                toUpdate.size(),
                unchangedPrices,
                skippedRows,
                (System.nanoTime() - classifyStart) / 1_000_000
        );

        editorialPriceBatchRepository.insertBatch(
                job.getProvider().getId(),
                job.getValidFrom(),
                toInsert
        );

        editorialPriceBatchRepository.updateBatch(
                toUpdate
        );

        int accountedRows =
                createdPrices
                        + updatedPrices
                        + unchangedPrices
                        + skippedRows;

        if (accountedRows != rows.size()) {
            log.warn(
                    "Price batch counters mismatch. "
                            + "rows={} created={} updated={} "
                            + "unchanged={} skipped={} accounted={}",
                    rows.size(),
                    createdPrices,
                    updatedPrices,
                    unchangedPrices,
                    skippedRows,
                    accountedRows
            );
        }

        return new PriceImportCounters(
                createdPrices,
                updatedPrices,
                unchangedPrices,
                skippedRows
        );
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<EditorialPrice> findCurrentByBookId(Long bookId) {
        return editorialPriceRepository
                .findFirstByBookIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
                        bookId,
                        LocalDate.now(ZoneId.systemDefault())
                );
    }

    private List<EditorialPrice> loadExistingPrices(List<Long> bookIds, PriceListImportJob job) {
        return editorialPriceRepository
                .findByBookIdInAndProviderIdAndValidFrom(
                        bookIds,
                        job.getProvider().getId(),
                        job.getValidFrom()
                );
    }

    private void validateOrigin(PriceListImportJob job) {
        if (
                job.getProvider() == null
                        || job.getImportConfig() == null
        ) {
            throw new BusinessException(
                    "La importación no tiene un proveedor o una configuración válida."
            );
        }
    }
}
