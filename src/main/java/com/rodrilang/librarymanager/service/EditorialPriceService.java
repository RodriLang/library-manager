package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.internal.EditorialPriceImportResult;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
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
            PriceListProvider provider,
            LocalDate validFrom
    );

    PriceImportCounters registerOrUpdateBatchForImport(
            List<Book> books,
            List<PriceListRow> rows,
            PriceListImportJob job
    );

    Optional<EditorialPrice> findCurrentByBookId(Long bookId);
}