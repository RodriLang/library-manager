package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.importer.price.configuration.dto.ProviderBookRegistrationResult;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.model.Book;

import java.util.List;

public interface ProviderBookService {

    ProviderBookRegistrationResult registerOrUpdate(PriceListProvider provider, Book book, String externalCode);

    void registerBatch(PriceListProvider provider, List<Book> books, List<PriceListRow> rows);
}