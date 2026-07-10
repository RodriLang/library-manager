package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.internal.EditorialPriceImportResult;
import com.rodrilang.librarymanager.enums.EditorialPriceChangeType;
import com.rodrilang.librarymanager.importer.price.dto.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            LocalDate validFrom
    ) {
        if (books.isEmpty()) {
            return new PriceImportCounters(0, 0, 0);
        }

        PriceListSource source = rows.getFirst().priceListSource();

        List<Long> bookIds = books.stream()
                .map(Book::getId)
                .distinct()
                .toList();

        Map<Long, EditorialPrice> existingByBookId = editorialPriceRepository
                .findByBookIdInAndSourceAndValidFrom(bookIds, source, validFrom)
                .stream()
                .collect(Collectors.toMap(
                        price -> price.getBook().getId(),
                        Function.identity()
                ));

        List<EditorialPrice> toSave = new ArrayList<>();

        int createdPrices = 0;
        int updatedPrices = 0;
        int unchangedPrices = 0;

        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);
            PriceListRow row = rows.get(i);

            EditorialPrice existing = existingByBookId.get(book.getId());

            if (existing == null) {
                EditorialPrice newPrice = EditorialPrice.builder()
                        .book(book)
                        .price(row.retailPrice())
                        .currency("ARS")
                        .source(row.priceListSource())
                        .validFrom(validFrom)
                        .active(true)
                        .build();

                toSave.add(newPrice);
                existingByBookId.put(book.getId(), newPrice);

                createdPrices++;
                continue;
            }

            if (existing.getPrice().compareTo(row.retailPrice()) == 0) {
                unchangedPrices++;
                continue;
            }

            existing.setPrice(row.retailPrice());

            if (!toSave.contains(existing)) {
                toSave.add(existing);
            }

            updatedPrices++;
        }

        if (!toSave.isEmpty()) {
            editorialPriceRepository.saveAll(toSave);
        }

        return new PriceImportCounters(
                createdPrices,
                updatedPrices,
                unchangedPrices
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
}
