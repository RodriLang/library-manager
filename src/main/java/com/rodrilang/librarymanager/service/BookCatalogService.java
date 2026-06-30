package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.model.Book;

public interface BookCatalogService {

    Book getOrCreateByIsbn(String isbn);

}
