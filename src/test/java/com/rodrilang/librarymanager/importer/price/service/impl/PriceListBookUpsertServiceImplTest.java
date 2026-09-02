package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.dto.internal.BookImportResult;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.importer.price.dto.internal.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.resolver.AuthorResolver;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListIdentifierResolver;
import com.rodrilang.librarymanager.importer.price.resolver.PublisherResolver;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class PriceListBookUpsertServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private AuthorResolver authorResolver;

    @Mock
    private PublisherResolver publisherResolver;

    private PriceListBookUpsertServiceImpl service;

    @BeforeEach
    void setUp() {
        IsbnService isbnService = new IsbnService();

        service = new PriceListBookUpsertServiceImpl(
                bookRepository,
                authorResolver,
                publisherResolver,
                isbnService,
                new PriceListIdentifierResolver(isbnService)
        );
    }

    @Test
    void shouldFindExistingBookByConvertedIsbn10() {
        Book existingBook = Book.builder()
                .id(1L)
                .isbn13("9780804429573")
                .title("Test book")
                .build();

        ImportContext context = new ImportContext(
                null,
                new HashMap<>(
                        Map.of(
                                "9780804429573",
                                existingBook
                        )
                ),
                new HashMap<>(),
                new HashMap<>(
                        Map.of(
                                "9780804429573",
                                existingBook
                        )
                ),
                new HashMap<>(),
                new HashMap<>(),
                new HashSet<>(),
                new HashMap<>(),
                new HashMap<>()
        );

        PriceListRow row =
                createRow(
                        "080442957X",
                        "Test book"
                );

        BookImportResult result =
                service.findOrCreate(row, context);

        assertFalse(result.created());
        assertSame(existingBook, result.book());

        assertEquals(
                "080442957X",
                existingBook.getIsbn10()
        );

        assertEquals(
                "9780804429573",
                existingBook.getIsbn13()
        );

        assertSame(
                existingBook,
                context.booksByIsbn10()
                        .get("080442957X")
        );

        assertSame(
                existingBook,
                context.booksByCanonicalIsbn()
                        .get("9780804429573")
        );
    }

    private PriceListRow createRow(
            String isbn,
            String title
    ) {
        return new PriceListRow(
                2,
                isbn,
                title,
                null,
                null,
                new BigDecimal("1000"),
                null,
                BookSource.EXTERNAL_METADATA,
                null
        );
    }
}