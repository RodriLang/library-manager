package com.rodrilang.librarymanager.metadata.enrichment;

import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.metadata.BookMetadata;
import com.rodrilang.librarymanager.metadata.BookMetadataService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.service.AuthorService;
import com.rodrilang.librarymanager.service.PublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookMetadataEnrichmentServiceImpl implements BookMetadataEnrichmentService {

    private final BookRepository bookRepository;
    private final BookMetadataService bookMetadataService;
    private final PublisherService publisherService;
    private final AuthorService authorService;

    @Override
    @Transactional
    public int enrichPendingBooks(int limit) {
        List<Book> books = bookRepository.findBooksPendingMetadataEnrichment(
                PageRequest.of(0, limit)
        );

        int updated = 0;

        for (Book book : books) {
            if (enrich(book)) {
                updated++;
            }
        }

        return updated;
    }

    @Override
    @Transactional
    public boolean enrichBook(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Libro no encontrado"));

        return enrich(book);
    }

    private boolean enrich(Book book) {
        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            return false;
        }

        Optional<BookMetadata> metadataOptional = bookMetadataService.findByIsbn(book.getIsbn());

        if (metadataOptional.isEmpty()) {
            return false;
        }

        BookMetadata metadata = metadataOptional.get();
        boolean changed = false;

        if (isBlank(book.getSubtitle()) && hasText(metadata.subtitle())) {
            book.setSubtitle(metadata.subtitle());
            changed = true;
        }

        if (isBlank(book.getDescription()) && hasText(metadata.description())) {
            book.setDescription(metadata.description());
            changed = true;
        }

        if (isBlank(book.getLanguage()) && hasText(metadata.language())) {
            book.setLanguage(metadata.language());
            changed = true;
        }

        if (book.getPageCount() == null && metadata.pageCount() != null) {
            book.setPageCount(metadata.pageCount());
            changed = true;
        }

        if (book.getPublicationDate() == null && metadata.publicationDate() != null) {
            book.setPublicationDate(metadata.publicationDate());
            changed = true;
        }

        if (isBlank(book.getCoverUrl()) && hasText(metadata.coverUrl())) {
            book.setCoverUrl(metadata.coverUrl());
            changed = true;
        }

        if (book.getPublisher() == null && hasText(metadata.publisher())) {
            Publisher publisher = publisherService.findOrCreateByName(metadata.publisher());
            book.setPublisher(publisher);
            changed = true;
        }

        if ((book.getAuthors() == null || book.getAuthors().isEmpty())
                && metadata.authors() != null
                && !metadata.authors().isEmpty()) {

            Set<Author> authors = metadata.authors()
                    .stream()
                    .map(authorService::findOrCreateByName)
                    .collect(Collectors.toSet());

            book.setAuthors(authors);
            changed = true;
        }

        return changed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}