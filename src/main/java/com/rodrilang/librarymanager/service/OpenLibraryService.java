package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.model.Book;

import java.util.Optional;

public interface OpenLibraryService {

    Optional<Book> findByIsbn(String isbn);
}