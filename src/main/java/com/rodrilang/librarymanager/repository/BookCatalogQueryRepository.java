package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.criteria.BookCatalogCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookCatalogQueryRepository {

    Page<Book> findAll(
            BookCatalogCriteria criteria,
            long bookstoreId,
            Pageable pageable
    );

    Page<Book> searchByIsbn(
            BookCatalogCriteria criteria,
            String query,
            long bookstoreId,
            Pageable pageable
    );

    Page<Book> searchText(
            BookCatalogCriteria criteria,
            String query,
            String fullTextQuery,
            long bookstoreId,
            Pageable pageable
    );
}