package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.internal.EditorialPriceImportResult;
import com.rodrilang.librarymanager.importer.price.dto.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.EditorialPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EditorialPriceService {

    EditorialPriceImportResult registerOrUpdateForImport(
            Book book,
            BigDecimal price,
            PriceListSource source,
            LocalDate validFrom
    );

    PriceImportCounters registerOrUpdateBatchForImport(
            List<Book> books,
            List<PriceListRow> rows,
            LocalDate validFrom
    );

    Optional<EditorialPrice> findCurrentByBookId(Long bookId);
}