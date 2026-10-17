package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.enums.CoverSearchStatus;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportProviderRowJdbcRepository;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookIdentifierStatus;
import com.rodrilang.librarymanager.purchasing.repository.BookstoreProviderCatalogJdbcRepository;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookstoreProviderCatalogBatchService {
    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final IsbnService isbnService;
    private final BookstoreProviderCatalogJdbcRepository catalogJdbcRepository;
    private final InventoryPriceImportProviderRowJdbcRepository stagingRepository;

    @Transactional
    public BatchResult process(long importId, long bookstoreId, long providerId, LocalDate effectiveFrom,
                               List<InventoryPriceImportProviderRowJdbcRepository.StoredRow> rows) {
        if (rows.isEmpty()) return new BatchResult(0,0,0,0,0);

        List<RowContext> contexts = rows.stream().map(this::context).toList();
        Set<String> isbn13s = contexts.stream().filter(c -> c.isbn().valid()).map(c -> c.isbn().isbn13())
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> isbn10s = contexts.stream().filter(c -> c.isbn().valid()).map(c -> c.isbn().isbn10())
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Book> by13 = isbn13s.isEmpty() ? Map.of() : bookRepository.findByIsbn13InAndActiveTrue(isbn13s).stream()
                .filter(b -> b.getIsbn13() != null).collect(Collectors.toMap(Book::getIsbn13, Function.identity(), (a,b)->a));
        Map<String, Book> by10 = isbn10s.isEmpty() ? Map.of() : bookRepository.findByIsbn10InAndActiveTrue(isbn10s).stream()
                .filter(b -> b.getIsbn10() != null).collect(Collectors.toMap(Book::getIsbn10, Function.identity(), (a,b)->a));

        Set<String> externalCodes = contexts.stream().map(c -> text(c.row().externalCode()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Long> externalOwners = catalogJdbcRepository.findBooksByExternalCodes(providerId, externalCodes);
        Map<Long, Book> booksById = bookRepository.findAllById(new LinkedHashSet<>(externalOwners.values())).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity(), (a,b)->a));

        Map<RowContext, Book> resolved = new LinkedHashMap<>();
        List<RowContext> missing = new ArrayList<>();
        for (RowContext c : contexts) {
            Book book = null;
            if (c.isbn().valid()) {
                book = by13.get(c.isbn().isbn13());
                if (book == null && c.isbn().isbn10() != null) book = by10.get(c.isbn().isbn10());
            }
            if (book == null) {
                String externalCode = text(c.row().externalCode());
                Long linkedBookId = externalCode == null ? null : externalOwners.get(externalCode);
                if (linkedBookId != null) book = booksById.get(linkedBookId);
            }
            if (book != null) resolved.put(c, book);
            else if (c.isbn().valid() && text(c.row().title()) != null) missing.add(c);
        }

        createMissingBooks(missing, resolved);

        List<BookstoreProviderCatalogJdbcRepository.ProviderBookUpsert> links = new ArrayList<>();
        List<InventoryPriceImportProviderRowJdbcRepository.ResolvedBook> stagingUpdates = new ArrayList<>();
        Map<Long, List<InventoryPriceImportProviderRowJdbcRepository.StoredRow>> rowsByBook = new LinkedHashMap<>();
        int skipped = 0;
        Map<String, Long> batchExternalOwners = new HashMap<>(externalOwners);
        for (RowContext c : contexts) {
            Book book = resolved.get(c);
            if (book == null) { skipped++; continue; }
            String externalCode = text(c.row().externalCode());
            Long externalOwner = externalCode == null ? null : batchExternalOwners.get(externalCode);
            if (externalOwner != null && externalOwner.longValue() != book.getId().longValue()) {
                externalCode = null;
            } else if (externalCode != null) {
                batchExternalOwners.put(externalCode, book.getId());
            }
            links.add(new BookstoreProviderCatalogJdbcRepository.ProviderBookUpsert(
                    book.getId(), externalCode, text(c.row().isbn()), identifierStatus(c.isbn(), externalCode)));
            stagingUpdates.add(new InventoryPriceImportProviderRowJdbcRepository.ResolvedBook(c.row().id(), book.getId()));
            rowsByBook.computeIfAbsent(book.getId(), ignored -> new ArrayList<>()).add(c.row());
        }

        // Deduplicate links inside the same batch so PostgreSQL never attempts to
        // update the same ON CONFLICT target twice in one batch execution.
        Map<Long, BookstoreProviderCatalogJdbcRepository.ProviderBookUpsert> uniqueLinks = new LinkedHashMap<>();
        links.forEach(link -> uniqueLinks.merge(link.bookId(), link, (first, next) ->
                new BookstoreProviderCatalogJdbcRepository.ProviderBookUpsert(
                        first.bookId(),
                        first.externalCode() != null ? first.externalCode() : next.externalCode(),
                        first.reportedIsbn() != null ? first.reportedIsbn() : next.reportedIsbn(),
                        first.identifierStatus()
                )));
        catalogJdbcRepository.upsertProviderBooks(providerId, new ArrayList<>(uniqueLinks.values()));
        stagingRepository.updateResolvedBooks(stagingUpdates);

        return new BatchResult(rows.size(), resolved.size(), 0, skipped, 0);
    }

    private void createMissingBooks(List<RowContext> missing, Map<RowContext, Book> resolved) {
        if (missing.isEmpty()) return;
        Set<String> publisherKeys = missing.stream().map(c -> normalized(c.row().publisher())).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Publisher> publishers = publisherRepository.findByNameNormalizedIn(publisherKeys).stream()
                .collect(Collectors.toMap(Publisher::getNameNormalized, Function.identity(), (a,b)->a));
        List<Publisher> newPublishers = new ArrayList<>();
        for (RowContext c : missing) {
            String raw = text(c.row().publisher()); String key = normalized(raw);
            if (key != null && !publishers.containsKey(key)) {
                Publisher p = Publisher.builder().name(raw).nameNormalized(key).build(); publishers.put(key,p); newPublishers.add(p);
            }
        }
        if (!newPublishers.isEmpty()) publisherRepository.saveAll(newPublishers);

        Set<String> authorKeys = new LinkedHashSet<>();
        Map<String,String> authorNames = new LinkedHashMap<>();
        for (RowContext c : missing) for (String raw : authorPieces(c.row().author())) {
            String key = normalized(raw); if (key != null) { authorKeys.add(key); authorNames.putIfAbsent(key, raw); }
        }
        Map<String, Author> authors = authorRepository.findByNameNormalizedIn(authorKeys).stream()
                .collect(Collectors.toMap(Author::getNameNormalized, Function.identity(), (a,b)->a));
        List<Author> newAuthors = new ArrayList<>();
        for (String key : authorKeys) if (!authors.containsKey(key)) {
            Author a = Author.builder().name(authorNames.get(key)).nameNormalized(key).build(); authors.put(key,a); newAuthors.add(a);
        }
        if (!newAuthors.isEmpty()) authorRepository.saveAll(newAuthors);

        // Deduplicate missing rows by canonical ISBN within the batch.
        Map<String, Book> createdByKey = new LinkedHashMap<>();
        List<Book> newBooks = new ArrayList<>();
        for (RowContext c : missing) {
            String key = c.isbn().isbn13() != null ? "13:" + c.isbn().isbn13() : "10:" + c.isbn().isbn10();
            Book book = createdByKey.get(key);
            if (book == null) {
                Set<Author> rowAuthors = authorPieces(c.row().author()).stream().map(this::normalized)
                        .map(authors::get).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
                book = Book.builder()
                        .isbn10(c.isbn().isbn10()).isbn13(c.isbn().isbn13()).title(text(c.row().title()))
                        .publisher(publishers.get(normalized(c.row().publisher()))).authors(rowAuthors)
                        .source(BookSource.IMPORTED).catalogStatus(BookCatalogStatus.PENDING_REVIEW)
                        .coverSearchStatus(CoverSearchStatus.PENDING).active(true).build();
                createdByKey.put(key, book); newBooks.add(book);
            }
            resolved.put(c, book);
        }
        if (!newBooks.isEmpty()) bookRepository.saveAll(newBooks);
    }

    private RowContext context(InventoryPriceImportProviderRowJdbcRepository.StoredRow row) { return new RowContext(row, isbnService.parse(row.isbn())); }
    private ProviderBookIdentifierStatus identifierStatus(ParsedIsbn p, String externalCode) {
        if (!p.valid()) {
            return externalCode != null
                    ? ProviderBookIdentifierStatus.EXTERNAL_CODE
                    : ProviderBookIdentifierStatus.INVALID_UNRESOLVED;
        }
        return switch (p.status()) {
            case VALID -> p.isbn10()!=null ? ProviderBookIdentifierStatus.RECOVERED_FROM_ISBN10 : ProviderBookIdentifierStatus.VALID_ISBN;
            case RECOVERED_MISSING_CHECK_DIGIT -> ProviderBookIdentifierStatus.RECOVERED_MISSING_CHECK_DIGIT;
            case RECOVERED_INVALID_X -> ProviderBookIdentifierStatus.RECOVERED_INVALID_X;
            default -> ProviderBookIdentifierStatus.VALID_ISBN;
        };
    }
    private List<String> authorPieces(String raw) { String v=text(raw); return v==null?List.of():Arrays.stream(v.split("\\s*(?:;|\\|)\\s*")).map(this::text).filter(Objects::nonNull).toList(); }
    private String normalized(String raw) { String v=text(raw); return v==null?null:TextNormalizer.normalizeForMatch(v); }
    private String text(String v) { return v==null||v.isBlank()?null:v.trim(); }

    private record RowContext(InventoryPriceImportProviderRowJdbcRepository.StoredRow row, ParsedIsbn isbn) {}
    public record BatchResult(int processed, int resolved, int created, int skipped, int errors) {}
}
