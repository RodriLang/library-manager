package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.dto.internal.BookImportResult;
import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.enums.CoverCandidateStatus;
import com.rodrilang.librarymanager.importer.price.dto.internal.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListIdentifier;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListMetadata;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.enums.PriceListIdentifierType;
import com.rodrilang.librarymanager.importer.price.resolver.AuthorResolver;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListIdentifierResolver;
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
    private final PriceListIdentifierResolver identifierResolver;

    @Override
    public Book upsert(PriceListRow row, ImportContext context, LocalDate today) {
        ParsedIsbn parsedIsbn = isbnService.parse(row.isbn());
        Book book = findExistingBook(row, context);

        if (book == null) {
            Book newBook = createBook(row, context);
            registerBookInContext(newBook, row, context);
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
        registerBookInContext(newBook, row, context);
        return new BookImportResult(newBook, true);
    }

    private void registerBookInContext(Book book, PriceListRow row, ImportContext context) {
        PriceListIdentifier identifier = identifierResolver.resolve(row);

        if (identifier.type() == PriceListIdentifierType.ISBN) {
            if (hasText(book.getIsbn13())) {
                context.booksByIsbn13().put(book.getIsbn13(), book);
                context.booksByCanonicalIsbn().put(book.getIsbn13(), book);
            }

            if (hasText(book.getIsbn10())) {
                context.booksByIsbn10().put(book.getIsbn10(), book);
            }

            return;
        }

        if (identifier.type() == PriceListIdentifierType.EXTERNAL_CODE) {
            context.booksByExternalCode().put(identifier.externalCode(), book);
        }
    }

    private Book createBook(
            PriceListRow row,
            ImportContext context
    ) {
        PriceListIdentifier identifier =
                identifierResolver.resolve(row);

        PriceListMetadata metadata = row.metadata();

        Book book = Book.builder()
                .isbn10(identifier.isbn10())
                .isbn13(identifier.isbn13())
                .title(row.title().trim())
                .description(resolveDescription(metadata))
                .language(resolveLanguage(metadata))
                .pageCount(resolvePageCount(metadata))
                .publicationDate(resolvePublicationDate(metadata))
                .coverUrl(null)
                .coverSource(null)
                .widthCm(metadata != null ? metadata.widthCm() : null)
                .heightCm(metadata != null ? metadata.heightCm() : null)
                .depthCm(metadata != null ? metadata.depthCm() : null)
                .weightGrams(metadata != null ? metadata.weightGrams() : null)
                .source(BookSource.EDITORIAL_PRICE_LIST)
                .catalogStatus(BookCatalogStatus.VERIFIED)
                .categoryName(formatNullable(row.categoryName()))
                .publisher(publisherResolver.resolve(row, context))
                .authors(authorResolver.resolve(row, context))
                .active(true)
                .build();


        if (metadata != null && hasText(metadata.sourceCoverUrl())) {
            book.registerCoverCandidate(metadata.sourceCoverUrl());
        }

        return book;
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

        if (hasText(book.getIsbn13())) {
            context.booksByIsbn13().put(book.getIsbn13(), book);
        }

        if (hasText(book.getIsbn10())) {
            context.booksByIsbn10().put(book.getIsbn10(), book);
        }

        context.booksByCanonicalIsbn().put(canonicalIsbn, book);
    }

    private void updateExistingBook(Book book, PriceListRow row, ImportContext context) {
        if (!hasText(book.getTitle()) && hasText(row.title())) {
            book.setTitle(row.title().trim());
        }

        if (book.getPublisher() == null && hasText(row.publisherName())) {
            book.setPublisher(publisherResolver.resolve(row, context));
        }

        if ((book.getAuthors() == null || book.getAuthors().isEmpty()) && hasText(row.authorName())) {
            book.setAuthors(authorResolver.resolve(row, context));
        }

        updateMetadata(book, row.metadata());

    }

    private void updateMetadata(Book book, PriceListMetadata metadata) {
        if (metadata == null) {
            return;
        }

        if (!hasText(book.getDescription()) && hasText(metadata.description())) {
            book.setDescription(metadata.description().trim());
        }

        if (!hasText(book.getLanguage()) && hasText(metadata.language())) {
            book.setLanguage(metadata.language().trim());
        }

        if (book.getPageCount() == null && metadata.pageCount() != null) {
            book.setPageCount(metadata.pageCount());
        }

        if (book.getPublicationDate() == null && metadata.publicationDate() != null) {
            book.setPublicationDate(metadata.publicationDate());
        }

        if (hasText(metadata.sourceCoverUrl()) && !hasText(book.getCoverUrl())) {
            book.registerCoverCandidate(metadata.sourceCoverUrl());
        }

        if (book.getWeightGrams() == null && metadata.weightGrams() != null) {
            book.setWeightGrams(metadata.weightGrams());
        }

        if (book.getWidthCm() == null && metadata.widthCm() != null) {
            book.setWidthCm(metadata.widthCm());
        }

        if (book.getHeightCm() == null && metadata.heightCm() != null) {
            book.setHeightCm(metadata.heightCm());
        }

        if (book.getDepthCm() == null && metadata.depthCm() != null) {
            book.setDepthCm(metadata.depthCm());
        }
    }

    private Book findExistingBook(PriceListRow row, ImportContext context) {
        PriceListIdentifier identifier = identifierResolver.resolve(row);

        if (identifier.type() == PriceListIdentifierType.ISBN) {
            Book exactByIsbn13 = context.booksByIsbn13().get(identifier.isbn13());

            if (exactByIsbn13 != null) {
                return exactByIsbn13;
            }

            if (identifier.isbn10() != null) {
                Book exactByIsbn10 = context.booksByIsbn10().get(identifier.isbn10());

                if (exactByIsbn10 != null) {
                    return exactByIsbn10;
                }
            }

            return context.booksByCanonicalIsbn().get(identifier.isbn13());
        }

        if (identifier.type() == PriceListIdentifierType.EXTERNAL_CODE) {
            return context.booksByExternalCode().get(identifier.externalCode());
        }

        if (hasText(row.publisherName())) {
            return bookRepository.findFirstByTitleIgnoreCaseAndPublisher_NameIgnoreCase(
                    row.title().trim(),
                    row.publisherName().trim()
            ).orElse(null);
        }

        return bookRepository.findFirstByTitleIgnoreCase(row.title().trim()).orElse(null);
    }

    private String resolveDescription(PriceListMetadata metadata) {
        return metadata != null && hasText(metadata.description())
                ? metadata.description().trim()
                : null;
    }

    private String resolveLanguage(PriceListMetadata metadata) {
        return metadata != null && hasText(metadata.language())
                ? metadata.language().trim()
                : null;
    }

    private Integer resolvePageCount(PriceListMetadata metadata) {
        return metadata != null ? metadata.pageCount() : null;
    }

    private LocalDate resolvePublicationDate(PriceListMetadata metadata) {
        return metadata != null ? metadata.publicationDate() : null;
    }
}