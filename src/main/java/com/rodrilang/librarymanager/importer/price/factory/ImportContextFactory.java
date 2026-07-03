package com.rodrilang.librarymanager.importer.price.factory;

import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.resolver.AuthorResolver;
import com.rodrilang.librarymanager.importer.price.resolver.PublisherResolver;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.normalizeIsbn;

@Component
@RequiredArgsConstructor
public class ImportContextFactory {

    private final BookRepository bookRepository;
    private final AuthorResolver authorResolver;
    private final PublisherResolver publisherResolver;

    public ImportContext create(List<PriceListRow> rows) {
        Set<String> isbns = rows.stream()
                .map(row -> normalizeIsbn(row.isbn()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, Book> booksByIsbn = bookRepository.findByIsbnIn(isbns)
                .stream()
                .collect(Collectors.toMap(
                        book -> normalizeIsbn(book.getIsbn()),
                        Function.identity()
                ));

        Map<String, Publisher> publishersByName = publisherResolver.loadPublishers(rows);

        Map<String, Author> authorsByName = authorResolver.loadAuthors(rows);

        return new ImportContext(
                booksByIsbn,
                publishersByName,
                authorsByName
        );
    }
}
