package com.rodrilang.librarymanager.importer.price.factory;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.model.ProviderBook;
import com.rodrilang.librarymanager.importer.price.configuration.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.IsbnBookConflict;
import com.rodrilang.librarymanager.importer.price.dto.PriceListIdentifier;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        Map<String, Book> booksByIsbn13 = loadBooksByIsbn13(isbn13Values).stream()
                .filter(book -> book.getIsbn13() != null)
                .collect(Collectors.toMap(
                        Book::getIsbn13,
                        Function.identity(),
                        this::chooseCanonicalBook
                ));

        Map<String, Book> booksByIsbn10 = loadBooksByIsbn10(isbn10Values).stream()
                .filter(book -> book.getIsbn10() != null)
                .collect(Collectors.toMap(
                        Book::getIsbn10,
                        Function.identity(),
                        this::chooseCanonicalBook
                ));

        Map<String, Book> booksByCanonicalIsbn = new HashMap<>();
        Map<String, IsbnBookConflict> conflicts = new HashMap<>();

        booksByIsbn13.values().forEach(book ->
                registerCanonicalBook(book, booksByCanonicalIsbn, conflicts)
        );

        booksByIsbn10.values().forEach(book ->
                registerCanonicalBook(book, booksByCanonicalIsbn, conflicts)
        );

        Map<String, Book> booksByExternalCode = loadBooksByExternalCode(provider, externalCodes);

        Map<String, Publisher> publishersByName = publisherResolver.loadPublishers(rows);
        Map<String, Author> authorsByName = authorResolver.loadAuthors(rows);

        return new ImportContext(
                provider,
                booksByIsbn13,
                booksByIsbn10,
                booksByCanonicalIsbn,
                booksByExternalCode,
                conflicts,
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
        return loadInBatches(values, bookRepository::findByIsbn13In);
    }

    private List<Book> loadBooksByIsbn10(Set<String> values) {
        return loadInBatches(values, bookRepository::findByIsbn10In);
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
}