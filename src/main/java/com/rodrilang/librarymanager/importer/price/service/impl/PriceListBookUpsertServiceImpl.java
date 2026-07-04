package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.dto.internal.BookImportResult;
import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.resolver.AuthorResolver;
import com.rodrilang.librarymanager.importer.price.resolver.PublisherResolver;
import com.rodrilang.librarymanager.importer.price.service.PriceListBookUpsertService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.formatNullable;
import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.hasText;
import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.normalizeIsbn;

@Component
@RequiredArgsConstructor
public class PriceListBookUpsertServiceImpl implements PriceListBookUpsertService {

    private final BookRepository bookRepository;
    private final AuthorResolver authorResolver;
    private final PublisherResolver publisherResolver;

    @Override
    public Book upsert(
            PriceListRow row,
            ImportContext context,
            LocalDate today
    ) {
        String isbn = normalizeIsbn(row.isbn());

        Book book = findExistingBook(row, context, isbn);

        if (book == null) {
            Book newBook = createBook(row, context);

            if (isbn != null) {
                context.booksByIsbn().put(isbn, newBook);
            }

            return newBook;
        }

        updateExistingBook(book, row, context);
        return book;
    }

    @Override
    public boolean exists(
            PriceListRow row,
            ImportContext context
    ) {
        String isbn = normalizeIsbn(row.isbn());
        return findExistingBook(row, context, isbn) != null;
    }

    @Override
    public BookImportResult findOrCreate(PriceListRow row, ImportContext context) {

        String isbn = normalizeIsbn(row.isbn());

        Book existingBook = findExistingBook(row, context, isbn);

        if (existingBook != null) {
            return new BookImportResult(existingBook, false);
        }

        Book newBook = createBook(row, context);

        if (isbn != null) {
            context.booksByIsbn().put(isbn, newBook);
        }

        return new BookImportResult(newBook, true);
    }

    private Book createBook(
            PriceListRow row,
            ImportContext context
    ) {
        return Book.builder()
                .isbn(normalizeIsbn(row.isbn()))
                .title(row.title().trim())
                .source(BookSource.EDITORIAL_PRICE_LIST)
                .catalogStatus(BookCatalogStatus.VERIFIED)
                .categoryName(formatNullable(row.categoryName()))
                .publisher(publisherResolver.resolve(row, context))
                .authors(authorResolver.resolve(row, context))
                .active(true)
                .build();
    }

    private void updateExistingBook(
            Book book,
            PriceListRow row,
            ImportContext context
    ) {

        if (!hasText(book.getTitle()) && hasText(row.title())) {
            book.setTitle(row.title().trim());
        }

        if (book.getPublisher() == null && hasText(row.publisherName())) {
            book.setPublisher(publisherResolver.resolve(row, context));
        }

        if ((book.getAuthors() == null || book.getAuthors().isEmpty())
                && hasText(row.authorName())) {
            book.setAuthors(authorResolver.resolve(row, context));
        }
    }

    private Book findExistingBook(
            PriceListRow row,
            ImportContext context,
            String isbn
    ) {
        if (isbn != null) {
            return context.booksByIsbn().get(isbn);
        }

        if (hasText(row.publisherName())) {
            return bookRepository
                    .findFirstByTitleIgnoreCaseAndPublisher_NameIgnoreCase(
                            row.title().trim(),
                            row.publisherName().trim()
                    )
                    .orElse(null);
        }

        return bookRepository
                .findFirstByTitleIgnoreCase(row.title().trim())
                .orElse(null);
    }
}