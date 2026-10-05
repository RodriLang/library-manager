package com.rodrilang.librarymanager.provider.catalog.service;

import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.enums.CoverSearchStatus;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookIdentifierStatus;
import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookSource;
import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookVerificationStatus;
import com.rodrilang.librarymanager.provider.catalog.model.ProviderBook;
import com.rodrilang.librarymanager.provider.catalog.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookstoreCatalogObservationService {

    private final ProviderRepository providerRepository;
    private final ProviderBookRepository providerBookRepository;
    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final IsbnService isbnService;

    @Transactional
    public void observe(Long providerId, List<PriceListRow> rows) {
        observeAndResolve(providerId, rows);
    }

    /**
     * Registra como observados los libros que aparecen en una lista aplicada por
     * una librería y devuelve el Book resuelto para cada fila. No se ejecuta
     * durante el preview: cualquier efecto global queda diferido hasta APPLY.
     */
    @Transactional
    public Map<Integer, Book> observeAndResolve(Long providerId, List<PriceListRow> rows) {
        Map<Integer, Book> result = new LinkedHashMap<>();
        if (providerId == null || rows == null || rows.isEmpty()) {
            return result;
        }

        Provider provider = providerRepository.findById(providerId).orElse(null);
        if (provider == null || !provider.isActive()) {
            return result;
        }

        for (PriceListRow row : rows) {
            Book book = observeRow(provider, row);
            if (book != null) {
                result.put(row.rowNumber(), book);
            }
        }
        return result;
    }

    private Book observeRow(Provider provider, PriceListRow row) {
        ParsedIsbn isbn = isbnService.parse(row.isbn());
        if (!isbn.valid()) {
            return null;
        }

        Book book = bookRepository.findByIsbn13(isbn.isbn13()).orElse(null);
        if (book == null && isbn.isbn10() != null) {
            book = bookRepository.findByIsbn10(isbn.isbn10()).orElse(null);
        }

        if (book == null) {
            if (blank(row.title())) {
                return null;
            }
            book = Book.builder()
                    .isbn10(isbn.isbn10())
                    .isbn13(isbn.isbn13())
                    .title(row.title().trim())
                    .publisher(resolvePublisher(row.publisherName()))
                    .authors(resolveAuthors(row.authorName()))
                    .categoryName(clean(row.categoryName()))
                    .source(BookSource.IMPORTED)
                    .catalogStatus(BookCatalogStatus.PENDING_REVIEW)
                    .coverSearchStatus(CoverSearchStatus.PENDING)
                    .active(true)
                    .build();
            book = bookRepository.save(book);
        }

        ProviderBook link = providerBookRepository
                .findByProviderIdAndBookId(provider.getId(), book.getId())
                .orElse(null);

        Instant now = Instant.now();
        String externalCode = row.metadata() != null
                ? clean(row.metadata().externalCode())
                : null;

        if (link == null) {
            link = ProviderBook.builder()
                    .provider(provider)
                    .book(book)
                    .externalCode(externalCode)
                    .reportedIsbn(clean(row.isbn()))
                    .identifierStatus(identifierStatus(isbn))
                    .active(true)
                    .firstSeenAt(now)
                    .lastSeenAt(now)
                    .source(ProviderBookSource.BOOKSTORE_IMPORT)
                    .verificationStatus(ProviderBookVerificationStatus.OBSERVED)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        } else {
            if (externalCode != null && link.getExternalCode() == null) {
                link.setExternalCode(externalCode);
            }
            if (link.getReportedIsbn() == null) {
                link.setReportedIsbn(clean(row.isbn()));
            }
            link.setLastSeenAt(now);
            link.setActive(true);
            link.setUpdatedAt(now);
            if (link.getVerificationStatus() != ProviderBookVerificationStatus.VERIFIED) {
                link.setSource(ProviderBookSource.BOOKSTORE_IMPORT);
                link.setVerificationStatus(ProviderBookVerificationStatus.OBSERVED);
            }
        }
        providerBookRepository.save(link);
        return book;
    }

    private Publisher resolvePublisher(String raw) {
        String name = clean(raw);
        if (name == null) {
            return null;
        }
        String normalized = TextNormalizer.normalizeForMatch(name);
        return publisherRepository.findByNameNormalized(normalized)
                .orElseGet(() -> publisherRepository.save(Publisher.builder().name(name).build()));
    }

    private Set<Author> resolveAuthors(String raw) {
        String value = clean(raw);
        if (value == null) {
            return new LinkedHashSet<>();
        }
        Set<Author> authors = new LinkedHashSet<>();
        for (String piece : value.split("\\s*(?:;|\\|)\\s*")) {
            String name = clean(piece);
            if (name == null) {
                continue;
            }
            String normalized = TextNormalizer.normalizeForMatch(name);
            authors.add(authorRepository.findByNameNormalized(normalized)
                    .orElseGet(() -> authorRepository.save(Author.builder().name(name).build())));
        }
        return authors;
    }

    private ProviderBookIdentifierStatus identifierStatus(ParsedIsbn parsed) {
        return switch (parsed.status()) {
            case VALID -> parsed.isbn10() != null
                    ? ProviderBookIdentifierStatus.RECOVERED_FROM_ISBN10
                    : ProviderBookIdentifierStatus.VALID_ISBN;
            case RECOVERED_MISSING_CHECK_DIGIT -> ProviderBookIdentifierStatus.RECOVERED_MISSING_CHECK_DIGIT;
            case RECOVERED_INVALID_X -> ProviderBookIdentifierStatus.RECOVERED_INVALID_X;
            default -> ProviderBookIdentifierStatus.VALID_ISBN;
        };
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return blank(value) ? null : value.trim();
    }
}
