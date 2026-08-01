package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.dto.internal.BookImportResult;
import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.importer.price.dto.BookIsbnValues;
import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.resolver.AuthorResolver;
import com.rodrilang.librarymanager.importer.price.resolver.PublisherResolver;
import com.rodrilang.librarymanager.importer.price.service.PriceListBookUpsertService;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.formatNullable;
import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.hasText;

@Component
@RequiredArgsConstructor
public class PriceListBookUpsertServiceImpl implements PriceListBookUpsertService {

    private final BookRepository bookRepository;
    private final AuthorResolver authorResolver;
    private final PublisherResolver publisherResolver;
    private final IsbnService isbnService;

    @Override
    public Book upsert(PriceListRow row, ImportContext context, LocalDate today) {
        ParsedIsbn parsedIsbn = isbnService.parse(row.isbn());
        Book book = findExistingBook(row, context);

        if (book == null) {
            Book newBook = createBook(row, context);
            registerBookInContext(newBook, context);
            return newBook;
        }

        completeMissingIsbn(book, parsedIsbn, context);
        updateExistingBook(book, row, context);
        return book;
    }

    @Override
    public boolean exists(PriceListRow row, ImportContext context) {
        return findExistingBook(row, context) != null;
    }

    @Override
    public BookImportResult findOrCreate(PriceListRow row, ImportContext context) {
        ParsedIsbn parsedIsbn = isbnService.parse(row.isbn());
        Book existingBook = findExistingBook(row, context);

        if (existingBook != null) {
            completeMissingIsbn(existingBook, parsedIsbn, context);
            updateExistingBook(existingBook, row, context);
            return new BookImportResult(existingBook, false);
        }

        Book newBook = createBook(row, context);
        registerBookInContext(newBook, context);

        return new BookImportResult(newBook, true);
    }

    private void registerBookInContext(Book book, ImportContext context) {
        if (hasText(book.getIsbn13())) {
            context.booksByIsbn13().put(book.getIsbn13(), book);
        }

        if (hasText(book.getIsbn10())) {
            context.booksByIsbn10().put(book.getIsbn10(), book);
        }

        ParsedIsbn parsedIsbn = isbnService.parse(book.getPreferredIsbn());

        if (parsedIsbn.valid()) {
            context.booksByCanonicalIsbn().put(parsedIsbn.isbn13(), book);
        }
    }

    private Book createBook(PriceListRow row, ImportContext context) {
        BookIsbnValues isbnValues = resolveIsbnValues(row.isbn());

        return Book.builder()
                .isbn(isbnValues.legacyIsbn())
                .isbn10(isbnValues.isbn10())
                .isbn13(isbnValues.isbn13())
                .title(row.title().trim())
                .source(BookSource.EDITORIAL_PRICE_LIST)
                .catalogStatus(BookCatalogStatus.VERIFIED)
                .categoryName(formatNullable(row.categoryName()))
                .publisher(publisherResolver.resolve(row, context))
                .authors(authorResolver.resolve(row, context))
                .active(true)
                .build();
    }

    private BookIsbnValues resolveIsbnValues(String value) {
        String normalized = isbnService.normalize(value);
        ParsedIsbn parsedIsbn = isbnService.parse(normalized);

        if (parsedIsbn.valid()) {
            return new BookIsbnValues(
                    parsedIsbn.preferredIsbn(),
                    parsedIsbn.isbn10(),
                    parsedIsbn.isbn13()
            );
        }

        if (isbnService.hasIsbn13Format(normalized)) {
            return new BookIsbnValues(normalized, null, normalized);
        }

        if (isbnService.hasIsbn10Format(normalized)) {
            return new BookIsbnValues(normalized, normalized, null);
        }

        return new BookIsbnValues(null, null, null);
    }

    private void completeMissingIsbn(Book book, ParsedIsbn parsedIsbn, ImportContext context) {
        if (!parsedIsbn.valid()) {
            return;
        }

        String canonicalIsbn = parsedIsbn.isbn13();

        if (context.isbnConflicts().containsKey(canonicalIsbn)) {
            return;
        }

        if (!hasText(book.getIsbn13())) {
            book.setIsbn13(parsedIsbn.isbn13());
        }

        if (!hasText(book.getIsbn10()) && parsedIsbn.isbn10() != null) {
            book.setIsbn10(parsedIsbn.isbn10());
        }

        if (!hasText(book.getIsbn())) {
            book.setIsbn(parsedIsbn.preferredIsbn());
        }

        if (hasText(book.getIsbn13())) {
            context.booksByIsbn13().put(book.getIsbn13(), book);
        }

        if (hasText(book.getIsbn10())) {
            context.booksByIsbn10().put(book.getIsbn10(), book);
        }

        context.booksByCanonicalIsbn().put(canonicalIsbn, book);
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

    private Book findExistingBook(PriceListRow row, ImportContext context) {
        String normalized = isbnService.normalize(row.isbn());

        if (isbnService.hasIsbn13Format(normalized)) {
            Book exactByIsbn13 = context.booksByIsbn13().get(normalized);

            if (exactByIsbn13 != null) {
                return exactByIsbn13;
            }
        }

        if (isbnService.hasIsbn10Format(normalized)) {
            Book exactByIsbn10 = context.booksByIsbn10().get(normalized);

            if (exactByIsbn10 != null) {
                return exactByIsbn10;
            }
        }

        ParsedIsbn parsedIsbn = isbnService.parse(normalized);

        if (parsedIsbn.valid()) {
            Book canonicalBook = context.booksByCanonicalIsbn().get(parsedIsbn.isbn13());

            if (canonicalBook != null) {
                return canonicalBook;
            }
        }

        if (hasText(row.publisherName())) {
            return bookRepository.findFirstByTitleIgnoreCaseAndPublisher_NameIgnoreCase(
                    row.title().trim(),
                    row.publisherName().trim()
            ).orElse(null);
        }

        return bookRepository.findFirstByTitleIgnoreCase(row.title().trim()).orElse(null);
    }
}