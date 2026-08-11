package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.dto.response.BookProviderResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.ProviderBookRegistrationResult;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.model.Book;

import java.util.List;

public interface ProviderBookService {

    ProviderBookRegistrationResult registerOrUpdate(PriceListProvider provider, Book book, String externalCode);

    List<BookProviderResponse> findActiveProvidersByBookId(Long bookId);

    List<BookProviderResponse> getProvidersForBook(Long bookId);

    void registerBatch(PriceListProvider provider, List<Book> books, List<PriceListRow> rows);
}