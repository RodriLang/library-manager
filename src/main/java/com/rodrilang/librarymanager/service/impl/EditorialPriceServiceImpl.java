package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.internal.EditorialPriceImportResult;
import com.rodrilang.librarymanager.enums.EditorialPriceChangeType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EditorialPriceServiceImpl implements EditorialPriceService {

    private final EditorialPriceRepository editorialPriceRepository;

    @Override
    public EditorialPriceImportResult registerOrUpdateForImport(
            Book book,
            BigDecimal price,
            PriceListSource source,
            LocalDate validFrom
    ) {
        EditorialPrice existing = editorialPriceRepository
                .findByBookIdAndSourceAndValidFrom(
                        book.getId(),
                        source,
                        validFrom
                )
                .orElse(null);

        if (existing == null) {
            EditorialPrice created = EditorialPrice.builder()
                    .book(book)
                    .price(price)
                    .currency("ARS")
                    .source(source)
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
            return new PriceImportCounters(0, 0, 0);
        }

        validateOrigin(job);

        List<Long> bookIds = books.stream()
                .map(Book::getId)
                .distinct()
                .toList();

        Map<Long, EditorialPrice> existingByBookId = loadExistingPrices(bookIds, job).stream()
                .collect(Collectors.toMap(
                        price -> price.getBook().getId(),
                        Function.identity()
                ));

        List<EditorialPrice> toSave = new ArrayList<>();

        int createdPrices = 0;
        int updatedPrices = 0;
        int unchangedPrices = 0;

        Map<Long, PriceListRow> processedRowsByBookId = new HashMap<>();

        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);
            PriceListRow row = rows.get(i);
            EditorialPrice existing = existingByBookId.get(book.getId());

            PriceListRow previousRow = processedRowsByBookId.putIfAbsent(book.getId(), row);

            if (previousRow != null) {
                if (previousRow.retailPrice().compareTo(row.retailPrice()) != 0) {
                    log.warn(
                            "Conflicting prices for same book. bookId={} title='{}' firstRow={} firstIsbn={} firstPrice={} secondRow={} secondIsbn={} secondPrice={}",
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

            if (existing == null) {
                EditorialPrice newPrice = createPrice(book, row, job);

                toSave.add(newPrice);
                existingByBookId.put(book.getId(), newPrice);
                createdPrices++;
                continue;
            }

            if (existing.getPrice().compareTo(row.retailPrice()) == 0) {
                unchangedPrices++;
                continue;
            }

            log.warn(
                    "Editorial price updated during import. bookId={} title='{}' row={} isbn={} previousPrice={} newPrice={} providerId={} validFrom={}",
                    book.getId(),
                    book.getTitle(),
                    row.rowNumber(),
                    row.isbn(),
                    existing.getPrice(),
                    row.retailPrice(),
                    job.getProvider().getId(),
                    job.getValidFrom()
            );

            existing.setPrice(row.retailPrice());

            if (!toSave.contains(existing)) {
                toSave.add(existing);
            }

            updatedPrices++;
        }

        if (!toSave.isEmpty()) {
            editorialPriceRepository.saveAll(toSave);
        }

        return new PriceImportCounters(createdPrices, updatedPrices, unchangedPrices);
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
        if (job.getImportConfig() != null) {
            return editorialPriceRepository.findByBookIdInAndProviderIdAndValidFrom(
                    bookIds,
                    job.getProvider().getId(),
                    job.getValidFrom()
            );
        }

        return editorialPriceRepository.findByBookIdInAndSourceAndValidFrom(
                bookIds,
                job.getPriceListSource(),
                job.getValidFrom()
        );
    }

    private EditorialPrice createPrice(Book book, PriceListRow row, PriceListImportJob job) {
        return EditorialPrice.builder()
                .book(book)
                .price(row.retailPrice())
                .currency("ARS")
                .source(job.getPriceListSource())
                .provider(job.getProvider())
                .validFrom(job.getValidFrom())
                .active(true)
                .build();
    }

    private void validateOrigin(PriceListImportJob job) {
        boolean legacy = job.getPriceListSource() != null
                && job.getProvider() == null
                && job.getImportConfig() == null;

        boolean configurable = job.getPriceListSource() == null
                && job.getProvider() != null
                && job.getImportConfig() != null;

        if (!legacy && !configurable) {
            throw new BusinessException("La importación no tiene un origen válido.");
        }
    }


}
