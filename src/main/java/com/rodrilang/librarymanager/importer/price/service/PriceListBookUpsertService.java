package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.dto.internal.BookImportResult;
import com.rodrilang.librarymanager.importer.price.dto.internal.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.model.Book;

import java.time.LocalDate;

public interface PriceListBookUpsertService {

    Book upsert(PriceListRow row, ImportContext context, LocalDate today);

    boolean exists(PriceListRow row, ImportContext context);

    BookImportResult findOrCreate(PriceListRow row, ImportContext context);

}