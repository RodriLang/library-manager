package com.rodrilang.librarymanager.importer.price.factory;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.model.ProviderBook;
import com.rodrilang.librarymanager.importer.price.configuration.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.importer.price.dto.internal.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.IsbnBookConflict;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListIdentifier;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.resolver.AuthorResolver;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListIdentifierResolver;
import com.rodrilang.librarymanager.importer.price.resolver.PublisherResolver;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportContextFactory {

    @Value("${app.price-import.isbn-query-batch-size:1000}")
    private int isbnQueryBatchSize;

    private final BookRepository bookRepository;
    private final AuthorResolver authorResolver;
    private final PublisherResolver publisherResolver;
    private final IsbnService isbnService;
    private final PriceListIdentifierResolver identifierResolver;
    private final ProviderBookRepository providerBookRepository;

    public ImportContext create(List<PriceListRow> rows, PriceListProvider provider) {
        long startedAt = System.nanoTime();

        long stepStartedAt = System.nanoTime();

        Set<String> isbn13Values = new LinkedHashSet<>();
        Set<String> isbn10Values = new LinkedHashSet<>();
        Set<String> externalCodes = new LinkedHashSet<>();

        for (PriceListRow row : rows) {
            PriceListIdentifier identifier = identifierResolver.resolve(row);

            switch (identifier.type()) {
                case ISBN -> {
                    isbn13Values.add(identifier.isbn13());

                    if (identifier.isbn10() != null) {
                        isbn10Values.add(identifier.isbn10());
                    }
                }
                case EXTERNAL_CODE -> externalCodes.add(identifier.externalCode());
                case EMPTY -> {
                }
            }
        }

        long identifiersMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        List<Book> booksByIsbn13Loaded = loadBooksByIsbn13(isbn13Values);

        long isbn13LookupMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        Map<String, Book> booksByIsbn13 = booksByIsbn13Loaded.stream()
                .filter(book -> book.getIsbn13() != null)
                .collect(Collectors.toMap(
                        Book::getIsbn13,
                        Function.identity(),
                        this::chooseCanonicalBook
                ));

        long isbn13MappingMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        List<Book> booksByIsbn10Loaded = loadBooksByIsbn10(isbn10Values);

        long isbn10LookupMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        Map<String, Book> booksByIsbn10 = booksByIsbn10Loaded.stream()
                .filter(book -> book.getIsbn10() != null)
                .collect(Collectors.toMap(
                        Book::getIsbn10,
                        Function.identity(),
                        this::chooseCanonicalBook
                ));

        long isbn10MappingMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        Map<String, Book> booksByCanonicalIsbn = new HashMap<>();
        Map<String, IsbnBookConflict> conflicts = new HashMap<>();

        booksByIsbn13.values().forEach(book ->
                registerCanonicalBook(book, booksByCanonicalIsbn, conflicts)
        );

        booksByIsbn10.values().forEach(book ->
                registerCanonicalBook(book, booksByCanonicalIsbn, conflicts)
        );

        long canonicalizationMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        Map<String, Book> booksByExternalCode = loadBooksByExternalCode(provider, externalCodes);

        Set<Long> existingBookIds = new HashSet<>();

        booksByIsbn13.values().stream()
                .map(Book::getId)
                .filter(Objects::nonNull)
                .forEach(existingBookIds::add);

        booksByIsbn10.values().stream()
                .map(Book::getId)
                .filter(Objects::nonNull)
                .forEach(existingBookIds::add);

        booksByExternalCode.values().stream()
                .map(Book::getId)
                .filter(Objects::nonNull)
                .forEach(existingBookIds::add);

        long authorsPresenceStartedAt = System.nanoTime();

        Set<Long> bookIdsWithAuthors =
                existingBookIds.isEmpty()
                        ? new HashSet<>()
                        : new HashSet<>(
                        bookRepository.findBookIdsWithAuthors(existingBookIds)
                );

        long authorsPresenceLookupMs = elapsedMillis(authorsPresenceStartedAt);

        long externalCodeLookupMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        Map<String, Publisher> publishersByName = publisherResolver.loadPublishers(provider.getId(), rows);

        long publishersMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        Map<String, Author> authorsByName = authorResolver.loadAuthors(rows);

        long authorsMs = elapsedMillis(stepStartedAt);

        long totalMs = elapsedMillis(startedAt);

        long measuredMs =
                identifiersMs
                        + isbn13LookupMs
                        + isbn13MappingMs
                        + isbn10LookupMs
                        + isbn10MappingMs
                        + canonicalizationMs
                        + externalCodeLookupMs
                        + publishersMs
                        + authorsMs;

        log.info(
                "Price list import context timing. "
                        + "providerId={} "
                        + "rows={} "
                        + "isbn13Values={} "
                        + "isbn10Values={} "
                        + "externalCodes={} "
                        + "booksByIsbn13={} "
                        + "booksByIsbn10={} "
                        + "conflicts={} "
                        + "identifiers={}ms "
                        + "isbn13Lookup={}ms "
                        + "isbn13Mapping={}ms "
                        + "isbn10Lookup={}ms "
                        + "isbn10Mapping={}ms "
                        + "canonicalization={}ms "
                        + "externalCodeLookup={}ms "
                        + "publishers={}ms "
                        + "authors={}ms "
                        + "authorsPresenceLookup={}ms "
                        + "other={}ms "
                        + "total={}ms",
                provider.getId(),
                rows.size(),
                isbn13Values.size(),
                isbn10Values.size(),
                externalCodes.size(),
                booksByIsbn13.size(),
                booksByIsbn10.size(),
                conflicts.size(),
                identifiersMs,
                isbn13LookupMs,
                isbn13MappingMs,
                isbn10LookupMs,
                isbn10MappingMs,
                canonicalizationMs,
                externalCodeLookupMs,
                publishersMs,
                authorsMs,
                authorsPresenceLookupMs,
                Math.max(0L, totalMs - measuredMs),
                totalMs
        );

        return new ImportContext(
                provider,
                booksByIsbn13,
                booksByIsbn10,
                booksByCanonicalIsbn,
                booksByExternalCode,
                conflicts,
                bookIdsWithAuthors,
                publishersByName,
                authorsByName
        );
    }

    private void registerCanonicalBook(
            Book book,
            Map<String, Book> booksByCanonicalIsbn,
            Map<String, IsbnBookConflict> conflicts
    ) {
        ParsedIsbn parsedIsbn = parseBookIsbn(book);

        if (!parsedIsbn.valid() || parsedIsbn.isbn13() == null) {
            return;
        }

        String canonicalIsbn = parsedIsbn.isbn13();
        Book existing = booksByCanonicalIsbn.get(canonicalIsbn);

        if (existing == null) {
            booksByCanonicalIsbn.put(canonicalIsbn, book);
            return;
        }

        if (existing.getId().equals(book.getId())) {
            return;
        }

        Book canonicalBook = chooseCanonicalBook(existing, book);
        Book duplicateBook = canonicalBook.getId().equals(existing.getId()) ? book : existing;

        booksByCanonicalIsbn.put(canonicalIsbn, canonicalBook);
        conflicts.put(canonicalIsbn, new IsbnBookConflict(
                canonicalIsbn,
                parsedIsbn.isbn10(),
                canonicalBook,
                duplicateBook
        ));
    }

    private Map<String, Book> loadBooksByExternalCode(
            PriceListProvider provider,
            Set<String> externalCodes
    ) {
        if (provider == null || externalCodes.isEmpty()) {
            return new HashMap<>();
        }

        return providerBookRepository
                .findByProviderIdAndExternalCodeIn(provider.getId(), externalCodes)
                .stream()
                .collect(Collectors.toMap(
                        ProviderBook::getExternalCode,
                        ProviderBook::getBook,
                        (existing, repeated) -> existing
                ));
    }

    private ParsedIsbn parseBookIsbn(Book book) {
        if (book.getIsbn13() != null) {
            ParsedIsbn parsedIsbn13 = isbnService.parse(book.getIsbn13());

            if (parsedIsbn13.valid()) {
                return parsedIsbn13;
            }
        }

        return isbnService.parse(book.getIsbn10());
    }

    private Book chooseCanonicalBook(Book first, Book second) {
        boolean firstHasIsbn13 = first.getIsbn13() != null;
        boolean secondHasIsbn13 = second.getIsbn13() != null;

        if (firstHasIsbn13 && !secondHasIsbn13) {
            return first;
        }

        if (secondHasIsbn13 && !firstHasIsbn13) {
            return second;
        }

        return first.getId() <= second.getId() ? first : second;
    }

    private List<Book> loadBooksByIsbn13(Set<String> values) {
        return loadInBatches(values, bookRepository::findForPriceImportByIsbn13In);
    }

    private List<Book> loadBooksByIsbn10(Set<String> values) {
        return loadInBatches(values, bookRepository::findForPriceImportByIsbn10In);
    }

    private List<Book> loadInBatches(
            Set<String> values,
            Function<Collection<String>, List<Book>> loader
    ) {
        if (values.isEmpty()) {
            return List.of();
        }

        List<String> valueList = new ArrayList<>(values);
        List<Book> books = new ArrayList<>();

        for (int fromIndex = 0; fromIndex < valueList.size(); fromIndex += isbnQueryBatchSize) {
            int toIndex = Math.min(fromIndex + isbnQueryBatchSize, valueList.size());
            books.addAll(loader.apply(valueList.subList(fromIndex, toIndex)));
        }

        return books;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}