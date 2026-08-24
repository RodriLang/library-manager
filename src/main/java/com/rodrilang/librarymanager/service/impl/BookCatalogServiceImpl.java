package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.exception.ManualBookRequiredException;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.metadata.BookMetadata;
import com.rodrilang.librarymanager.metadata.BookMetadataService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.service.BookCatalogService;
import com.rodrilang.librarymanager.util.StringUtils;
import com.rodrilang.librarymanager.util.TextNormalizer;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookCatalogServiceImpl implements BookCatalogService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final BookMetadataService bookMetadataService;
    private final IsbnService isbnService;

    @Transactional
    @Override
    public Book getOrCreateByIsbn(String isbn) {
        ParsedIsbn parsedIsbn = isbnService.parse(isbn);

        if (!parsedIsbn.valid()) {
            throw new ManualBookRequiredException(isbnService.normalize(isbn));
        }

        Book existingBook = findExistingBook(parsedIsbn);

        if (existingBook != null) {
            completeMissingIsbn(existingBook, parsedIsbn);
            return existingBook;
        }

        return createBookFromMetadata(parsedIsbn);
    }

    private Book findExistingBook(ParsedIsbn parsedIsbn) {
        Book byIsbn13 = bookRepository.findByIsbn13(parsedIsbn.isbn13()).orElse(null);

        if (byIsbn13 != null) {
            return byIsbn13;
        }

        if (parsedIsbn.isbn10() == null) {
            return null;
        }

        return bookRepository.findByIsbn10(parsedIsbn.isbn10()).orElse(null);
    }

    private Book createBookFromMetadata(ParsedIsbn parsedIsbn) {
        String preferredIsbn = parsedIsbn.preferredIsbn();

        BookMetadata metadata = bookMetadataService.findByIsbn(preferredIsbn)
                .orElseThrow(() -> new ManualBookRequiredException(preferredIsbn));

        if (metadata.title() == null || metadata.title().isBlank()) {
            throw new ManualBookRequiredException(preferredIsbn);
        }

        Publisher publisher = resolvePublisher(metadata.publisher());
        Set<Author> authors = resolveAuthors(metadata.authors());

        Book book = Book.builder()
                .isbn10(parsedIsbn.isbn10())
                .isbn13(parsedIsbn.isbn13())
                .title(metadata.title())
                .subtitle(metadata.subtitle())
                .description(metadata.description())
                .language(metadata.language())
                .pageCount(metadata.pageCount())
                .publicationYear(metadata.publicationYear())
                .publicationMonth(metadata.publicationMonth())
                .coverUrl(metadata.coverUrl())
                .source(BookSource.EXTERNAL_METADATA)
                .catalogStatus(BookCatalogStatus.PENDING_REVIEW)
                .publisher(publisher)
                .authors(authors)
                .active(true)
                .build();

        return bookRepository.save(book);
    }

    private void completeMissingIsbn(Book book, ParsedIsbn parsedIsbn) {
        if (book.getIsbn13() == null) {
            book.setIsbn13(parsedIsbn.isbn13());
        }

        if (book.getIsbn10() == null && parsedIsbn.isbn10() != null) {
            book.setIsbn10(parsedIsbn.isbn10());
        }
    }

    private Publisher resolvePublisher(String publisherName) {
        if (publisherName == null || publisherName.isBlank()) {
            return null;
        }

        String name = StringUtils.normalizeName(publisherName);
        String normalizedName = TextNormalizer.normalizeForMatch(name);

        return publisherRepository.findByNameNormalized(normalizedName)
                .orElseGet(() -> publisherRepository.save(
                        Publisher.builder()
                                .name(name)
                                .build()
                ));
    }

    private Set<Author> resolveAuthors(Set<String> authorNames) {
        if (authorNames == null || authorNames.isEmpty()) {
            return new LinkedHashSet<>();
        }

        return authorNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(this::resolveAuthor)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Author resolveAuthor(String authorName) {
        String name = StringUtils.normalizeName(authorName);
        String normalizedName = TextNormalizer.normalizeForMatch(name);

        return authorRepository.findByNameNormalized(normalizedName)
                .orElseGet(() -> authorRepository.save(
                        Author.builder()
                                .name(name)
                                .build()
                ));
    }
}