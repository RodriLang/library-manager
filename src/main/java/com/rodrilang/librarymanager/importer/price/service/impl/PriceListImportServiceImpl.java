package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportResult;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.parser.PriceListParser;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.importer.price.resolver.AuthorResolver;
import com.rodrilang.librarymanager.importer.price.resolver.PublisherResolver;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportService;
import com.rodrilang.librarymanager.importer.price.validator.PriceListRowValidator;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.BookRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.formatNullable;
import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.normalizeIsbn;
import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListImportServiceImpl implements PriceListImportService {

    private final List<PriceListParser> parsers;
    private final BookRepository bookRepository;
    private final AuthorResolver authorResolver;
    private final PublisherResolver publisherResolver;
    private final PriceListRowValidator rowValidator;

    @Transactional
    @Override
    public PriceListImportResult importPriceList(PriceListSource priceListSource, MultipartFile file) {
        log.info("Importing price list of {}", priceListSource);

        PriceListParser parser = resolveParser(priceListSource);

        List<PriceListRow> rows = parser.parse(file);
        List<PriceListImportError> errors = new ArrayList<>();

        List<PriceListRow> validRows = getValidRows(rows, errors);

        ImportContext context = preloadContext(validRows);

        int createdBooks = 0;
        int updatedBooks = 0;

        List<Book> booksToSave = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        for (PriceListRow row : validRows) {
            try {
                String isbn = normalizeIsbn(row.isbn());

                Book existingBook;

                if (isbn != null) {
                    existingBook = context.booksByIsbn().get(isbn);
                } else {
                    existingBook = bookRepository
                            .findFirstByTitleIgnoreCaseAndPriceListSource(
                                    row.title().trim(),
                                    row.priceListSource()
                            )
                            .orElse(null);
                }

                if (existingBook == null) {
                    Book newBook = createBook(row, context, today);
                    booksToSave.add(newBook);
                    context.booksByIsbn().put(isbn, newBook);
                    createdBooks++;
                } else {
                    updateExistingBook(existingBook, row, context, today);
                    booksToSave.add(existingBook);
                    updatedBooks++;
                }

            } catch (Exception ex) {
                errors.add(new PriceListImportError(
                        row.rowNumber(),
                        row.isbn(),
                        ex.getMessage()
                ));
            }
        }

        bookRepository.saveAll(booksToSave);

        int skippedRows = errors.size();

        return new PriceListImportResult(
                rows.size(),
                createdBooks,
                updatedBooks,
                skippedRows,
                errors
        );
    }

    private PriceListParser resolveParser(PriceListSource priceListSource) {
        return parsers.stream()
                .filter(parser -> parser.supports(priceListSource))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "No existe un parser configurado para la lista: " + priceListSource
                ));
    }

    private List<PriceListRow> getValidRows(
            List<PriceListRow> rows,
            List<PriceListImportError> errors
    ) {
        List<PriceListRow> validRows = new ArrayList<>();
        Set<String> repeatedIsbns = new HashSet<>();

        for (PriceListRow row : rows) {
            Optional<PriceListImportError> error = rowValidator.validateRow(row, repeatedIsbns);

            if (error.isPresent()) {
                errors.add(error.get());
            } else {
                validRows.add(row);
            }
        }

        return validRows;
    }

    private ImportContext preloadContext(List<PriceListRow> rows) {
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

    private Book createBook(
            PriceListRow row,
            ImportContext context,
            LocalDate today
    ) {
        return Book.builder()
                .isbn(normalizeIsbn(row.isbn()))
                .title(row.title().trim())
                .retailPrice(row.retailPrice())
                .retailPriceUpdatedAt(today)
                .source(BookSource.EDITORIAL_PRICE_LIST)
                .priceListSource(row.priceListSource())
                .categoryName(formatNullable(row.categoryName()))
                .publisher(publisherResolver.resolve(row, context))
                .authors(authorResolver.resolve(row, context))
                .active(true)
                .build();
    }

    private void updateExistingBook(
            Book book,
            PriceListRow row,
            ImportContext context,
            LocalDate today
    ) {
        book.setRetailPrice(row.retailPrice());
        book.setRetailPriceUpdatedAt(today);

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

}