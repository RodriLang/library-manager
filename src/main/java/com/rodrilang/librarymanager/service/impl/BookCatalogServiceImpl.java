package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.exception.ManualBookRequiredException;
import com.rodrilang.librarymanager.metadata.BookMetadata;
import com.rodrilang.librarymanager.metadata.BookMetadataService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.service.BookCatalogService;
import com.rodrilang.librarymanager.util.IsbnUtils;
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

    @Transactional
    @Override
    public Book getOrCreateByIsbn(String isbn) {

        String normalizedIsbn = IsbnUtils.normalize(isbn);

        return bookRepository.findByIsbn(normalizedIsbn)
                .orElseGet(() -> createBookFromMetadata(normalizedIsbn));
    }

    private Book createBookFromMetadata(String isbn) {

        String normalizedIsbn = IsbnUtils.normalize(isbn);

        BookMetadata metadata = bookMetadataService.findByIsbn(normalizedIsbn)
                .orElseThrow(() -> new ManualBookRequiredException(normalizedIsbn));

        if (metadata.title() == null || metadata.title().isBlank()) {
            throw new ManualBookRequiredException(normalizedIsbn);
        }

        Publisher publisher = resolvePublisher(metadata.publisher());
        Set<Author> authors = resolveAuthors(metadata.authors());

        Book book = Book.builder()
                .isbn(normalizedIsbn)
                .title(metadata.title())
                .subtitle(metadata.subtitle())
                .description(metadata.description())
                .language(metadata.language())
                .pageCount(metadata.pageCount())
                .publicationDate(metadata.publicationDate())
                .coverUrl(metadata.coverUrl())
                .source(BookSource.EXTERNAL_METADATA)
                .catalogStatus(BookCatalogStatus.PENDING_REVIEW)
                .publisher(publisher)
                .authors(authors)
                .active(true)
                .build();

        return bookRepository.save(book);
    }

    private Publisher resolvePublisher(String publisherName) {
        if (publisherName == null || publisherName.isBlank()) {
            return null;
        }

        String normalizedName = publisherName.trim();

        return publisherRepository.findByNameIgnoreCase(normalizedName)
                .orElseGet(() -> publisherRepository.save(
                        Publisher.builder()
                                .name(normalizedName)
                                .build()
                ));
    }

    private Set<Author> resolveAuthors(Set<String> authorNames) {
        if (authorNames == null || authorNames.isEmpty()) {
            return new LinkedHashSet<>();
        }

        return authorNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .map(this::resolveAuthor)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Author resolveAuthor(String authorName) {
        return authorRepository.findByNameIgnoreCase(authorName)
                .orElseGet(() -> authorRepository.save(
                        Author.builder()
                                .name(authorName)
                                .build()
                ));
    }
}