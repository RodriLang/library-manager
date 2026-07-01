package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.request.AddAuthorsRequest;
import com.rodrilang.librarymanager.dto.request.BookRequest;
import com.rodrilang.librarymanager.dto.request.UpdateBookRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.model.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookService {

    BookDetailResponse create(BookRequest request);

    BookDetailResponse addAuthors(Long bookId, AddAuthorsRequest request);

    BookDetailResponse getById(Long id);

    BookDetailResponse getByIsbn(String isbn);

    BookDetailResponse lookupByIsbn(String isbn);

    BookDetailResponse update(Long bookId, UpdateBookRequest request);

    Page<BookSummaryResponse> search(String query, Pageable pageable);

    Page<BookSummaryResponse> getAll(Pageable pageable);

    Book getEntityById(Long id);

    Book getEntityByIsbn(String isbn);

}
