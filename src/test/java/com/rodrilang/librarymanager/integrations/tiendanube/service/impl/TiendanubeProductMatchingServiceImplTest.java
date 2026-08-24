package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.RemoteInventoryMatch;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeProductMatchingServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private TiendanubeProductLinkRepository productLinkRepository;

    @Mock
    private IsbnService isbnService;

    private TiendanubeProductMatchingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TiendanubeProductMatchingServiceImpl(
                inventoryRepository,
                bookRepository,
                productLinkRepository,
                isbnService
        );
    }

    @Test
    void findBookCandidatesShouldReturnEmptyWhenProductNameIsBlank() {
        TiendanubeProductResponse product = product("");

        List<Book> result = service.findBookCandidates(product);

        assertThat(result).isEmpty();

        verify(bookRepository, never())
                .findTiendanubeCandidateIds(anyString(), anyInt());
    }

    @Test
    void findBookCandidatesShouldReturnEmptyWhenRepositoryFindsNoCandidates() {
        TiendanubeProductResponse product = product("Topadoras tóxicas");

        when(bookRepository.findTiendanubeCandidateIds(anyString(), anyInt()))
                .thenReturn(List.of());

        List<Book> result = service.findBookCandidates(product);

        assertThat(result).isEmpty();

        verify(bookRepository, never())
                .findAllWithDetailsByIdIn(anyList());
    }

    @Test
    void findBookCandidatesShouldMatchSingularRemoteTitleWithPluralCatalogTitle() {
        TiendanubeProductResponse product =
                product("Traficante de libros - Rosario Esposito La Rossa");

        Book correctBook = book(
                10L,
                "traficantes libros"
        );

        when(bookRepository.findTiendanubeCandidateIds(anyString(), anyInt()))
                .thenReturn(List.of(10L));

        when(bookRepository.findAllWithDetailsByIdIn(List.of(10L)))
                .thenReturn(List.of(correctBook));

        List<Book> result = service.findBookCandidates(product);

        assertThat(result)
                .containsExactly(correctBook);
    }

    @Test
    void findBookCandidatesShouldKeepPartiallyMatchingTitleAsCandidate() {
        TiendanubeProductResponse product =
                product("Topadoras tóxicas");

        Book topadorasOxidadas = book(
                20L,
                "topadoras oxidadas"
        );

        when(bookRepository.findTiendanubeCandidateIds(anyString(), anyInt()))
                .thenReturn(List.of(20L));

        when(bookRepository.findAllWithDetailsByIdIn(List.of(20L)))
                .thenReturn(List.of(topadorasOxidadas));

        List<Book> result = service.findBookCandidates(product);

        assertThat(result)
                .containsExactly(topadorasOxidadas);
    }

    @Test
    void findBookCandidatesShouldRankFullTitleAboveGenericSingleWordTitle() {
        TiendanubeProductResponse product =
                product("Traficante de libros - Rosario Esposito La Rossa");

        Book genericBook = book(
                1L,
                "libros"
        );

        Book correctBook = book(
                2L,
                "traficantes libros"
        );

        when(bookRepository.findTiendanubeCandidateIds(anyString(), anyInt()))
                .thenReturn(List.of(1L, 2L));

        when(bookRepository.findAllWithDetailsByIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(genericBook, correctBook));

        List<Book> result = service.findBookCandidates(product);

        assertThat(result).isNotEmpty();
        assertThat(result.getFirst()).isSameAs(correctBook);
    }

    @Test
    void findBookCandidatesShouldRankFullTitleAboveAuthorWordUsedAsTitle() {
        TiendanubeProductResponse product =
                product("Traficante de libros - Rosario Esposito La Rossa");

        Book rosario = book(
                1L,
                "rosario"
        );

        Book correctBook = book(
                2L,
                "traficantes libros"
        );

        when(bookRepository.findTiendanubeCandidateIds(anyString(), anyInt()))
                .thenReturn(List.of(1L, 2L));

        when(bookRepository.findAllWithDetailsByIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(rosario, correctBook));

        List<Book> result = service.findBookCandidates(product);

        assertThat(result).isNotEmpty();
        assertThat(result.getFirst()).isSameAs(correctBook);
    }

    @Test
    void findBookCandidatesShouldUseAuthorAsSecondaryRankingSignal() {
        TiendanubeProductResponse product =
                product("Los jardines - María López");

        Author mariaLopez = author("María López");
        Author pedroGonzalez = author("Pedro González");

        Book matchingAuthor = book(
                1L,
                "jardines",
                mariaLopez
        );

        Book differentAuthor = book(
                2L,
                "jardines",
                pedroGonzalez
        );

        when(bookRepository.findTiendanubeCandidateIds(anyString(), anyInt()))
                .thenReturn(List.of(2L, 1L));

        when(bookRepository.findAllWithDetailsByIdIn(List.of(2L, 1L)))
                .thenReturn(List.of(differentAuthor, matchingAuthor));

        List<Book> result = service.findBookCandidates(product);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isSameAs(matchingAuthor);
    }

    @Test
    void findBookCandidatesShouldNotReturnCompletelyUnrelatedCandidate() {
        TiendanubeProductResponse product =
                product("Topadoras tóxicas");

        Book unrelatedBook = book(
                1L,
                "historia argentina"
        );

        when(bookRepository.findTiendanubeCandidateIds(anyString(), anyInt()))
                .thenReturn(List.of(1L));

        when(bookRepository.findAllWithDetailsByIdIn(List.of(1L)))
                .thenReturn(List.of(unrelatedBook));

        List<Book> result = service.findBookCandidates(product);

        assertThat(result).isEmpty();
    }

    @Test
    void findBookCandidatesShouldReturnAtMostFiveCandidates() {
        TiendanubeProductResponse product =
                product("Historia argentina");

        List<Long> candidateIds = List.of(
                1L,
                2L,
                3L,
                4L,
                5L,
                6L,
                7L
        );

        List<Book> books = candidateIds.stream()
                .map(id -> book(id, "historia argentina"))
                .toList();

        when(bookRepository.findTiendanubeCandidateIds(anyString(), anyInt()))
                .thenReturn(candidateIds);

        when(bookRepository.findAllWithDetailsByIdIn(candidateIds))
                .thenReturn(books);

        List<Book> result = service.findBookCandidates(product);

        assertThat(result).hasSize(5);
    }

    @Test
    void findBookCandidatesShouldBuildPrefixFullTextQuery() {
        TiendanubeProductResponse product =
                product("Traficante de libros");

        when(bookRepository.findTiendanubeCandidateIds(
                eq("traficante:* | libros:*"),
                eq(15)
        )).thenReturn(List.of());

        service.findBookCandidates(product);

        verify(bookRepository)
                .findTiendanubeCandidateIds(
                        "traficante:* | libros:*",
                        15
                );
    }

    @Test
    void findRemoteMatchShouldReturnExactBarcodeMatch() {
        Book book = bookWithIsbn(
                10L,
                "topadoras oxidadas",
                "9789871234567"
        );

        Inventory inventory = inventory(book);

        TiendanubeVariantResponse variant = variant(
                100L,
                null,
                "9789871234567"
        );

        TiendanubeProductResponse product = product(
                200L,
                "Topadoras oxidadas",
                variant
        );

        RemoteInventoryMatch result = service.findRemoteMatch(
                inventory,
                List.of(product)
        );

        assertThat(result).isNotNull();

        assertThat(result.productId())
                .isEqualTo(200L);

        assertThat(result.variantId())
                .isEqualTo(100L);

        assertThat(result.matchType())
                .isEqualTo(TiendanubeMatchType.EXACT_BARCODE);
    }

    @Test
    void findRemoteMatchShouldReturnExactSkuMatch() {
        Book book = bookWithIsbn(
                10L,
                "topadoras oxidadas",
                "9789871234567"
        );

        Inventory inventory = inventory(book);

        TiendanubeVariantResponse variant = variant(
                100L,
                "9789871234567",
                null
        );

        TiendanubeProductResponse product = product(
                200L,
                "Topadoras oxidadas",
                variant
        );

        RemoteInventoryMatch result = service.findRemoteMatch(
                inventory,
                List.of(product)
        );

        assertThat(result).isNotNull();

        assertThat(result.productId())
                .isEqualTo(200L);

        assertThat(result.variantId())
                .isEqualTo(100L);

        assertThat(result.matchType())
                .isEqualTo(TiendanubeMatchType.EXACT_SKU);
    }

    @Test
    void findRemoteMatchShouldPreferExactMatchOverTextualMatch() {
        Book book = bookWithIsbn(
                10L,
                "topadoras oxidadas",
                "9789871234567"
        );

        Inventory inventory = inventory(book);

        TiendanubeProductResponse textualProduct = product(
                200L,
                "Topadoras oxidadas",
                variant(
                        100L,
                        null,
                        null
                )
        );

        TiendanubeProductResponse exactProduct = product(
                300L,
                "Producto con nombre completamente distinto",
                variant(
                        101L,
                        null,
                        "9789871234567"
                )
        );

        RemoteInventoryMatch result = service.findRemoteMatch(
                inventory,
                List.of(
                        textualProduct,
                        exactProduct
                )
        );

        assertThat(result).isNotNull();

        assertThat(result.productId())
                .isEqualTo(300L);

        assertThat(result.variantId())
                .isEqualTo(101L);

        assertThat(result.matchType())
                .isEqualTo(TiendanubeMatchType.EXACT_BARCODE);
    }

    @Test
    void findRemoteMatchShouldReturnPossibleMatchForSimilarText() {
        Book book = book(
                10L,
                "topadoras oxidadas"
        );

        Inventory inventory = inventory(book);

        TiendanubeProductResponse product = product(
                200L,
                "Topadoras tóxicas",
                variant(
                        100L,
                        null,
                        null
                )
        );

        RemoteInventoryMatch result = service.findRemoteMatch(
                inventory,
                List.of(product)
        );

        assertThat(result).isNotNull();

        assertThat(result.productId())
                .isEqualTo(200L);

        assertThat(result.variantId())
                .isEqualTo(100L);

        assertThat(result.matchType())
                .isEqualTo(TiendanubeMatchType.POSSIBLE_MATCH);
    }

    @Test
    void findRemoteMatchShouldReturnNullWhenNothingMatches() {
        Book book = book(
                10L,
                "topadoras oxidadas"
        );

        Inventory inventory = inventory(book);

        TiendanubeProductResponse product = product(
                200L,
                "Historia de la filosofía",
                variant(
                        100L,
                        null,
                        null
                )
        );

        RemoteInventoryMatch result = service.findRemoteMatch(
                inventory,
                List.of(product)
        );

        assertThat(result).isNull();
    }

    @Test
    void findRemoteMatchShouldReturnMultipleMatchesWhenSeveralProductsMatchTextually() {
        Book book = book(
                10L,
                "topadoras oxidadas"
        );

        Inventory inventory = inventory(book);

        TiendanubeProductResponse firstProduct = product(
                200L,
                "Topadoras oxidadas",
                variant(
                        100L,
                        null,
                        null
                )
        );

        TiendanubeProductResponse secondProduct = product(
                201L,
                "Topadoras oxidadas edición especial",
                variant(
                        101L,
                        null,
                        null
                )
        );

        RemoteInventoryMatch result = service.findRemoteMatch(
                inventory,
                List.of(
                        firstProduct,
                        secondProduct
                )
        );

        assertThat(result).isNotNull();

        assertThat(result.matchType())
                .isEqualTo(TiendanubeMatchType.MULTIPLE_MATCHES);

        assertThat(result.productId())
                .isNull();

        assertThat(result.variantId())
                .isNull();
    }

    private TiendanubeProductResponse product(String name) {
        TiendanubeProductResponse product =
                mock(TiendanubeProductResponse.class);

        lenient()
                .when(product.name())
                .thenReturn(Map.of("es", name));

        return product;
    }

    private TiendanubeProductResponse product(
            Long productId,
            String name,
            TiendanubeVariantResponse... variants
    ) {
        TiendanubeProductResponse product =
                mock(TiendanubeProductResponse.class);

        lenient()
                .when(product.id())
                .thenReturn(productId);

        lenient()
                .when(product.name())
                .thenReturn(Map.of("es", name));

        lenient()
                .when(product.variants())
                .thenReturn(List.of(variants));

        return product;
    }

    private TiendanubeVariantResponse variant(
            Long variantId,
            String sku,
            String barcode
    ) {
        TiendanubeVariantResponse variant =
                mock(TiendanubeVariantResponse.class);

        lenient()
                .when(variant.id())
                .thenReturn(variantId);

        lenient()
                .when(variant.sku())
                .thenReturn(sku);

        lenient()
                .when(variant.barcode())
                .thenReturn(barcode);

        return variant;
    }

    private Book book(
            Long id,
            String titleSearch,
            Author... authors
    ) {
        Book book = mock(Book.class);

        lenient()
                .when(book.getId())
                .thenReturn(id);

        lenient()
                .when(book.getTitleSearch())
                .thenReturn(titleSearch);

        lenient()
                .when(book.getAuthors())
                .thenReturn(Set.of(authors));

        return book;
    }

    private Book bookWithIsbn(
            Long id,
            String titleSearch,
            String isbn
    ) {
        Book book = book(
                id,
                titleSearch
        );

        lenient()
                .when(book.getPreferredIsbn())
                .thenReturn(isbn);

        return book;
    }

    private Author author(String name) {
        Author author = mock(Author.class);

        lenient()
                .when(author.getName())
                .thenReturn(name);

        return author;
    }

    private Inventory inventory(Book book) {
        Inventory inventory = mock(Inventory.class);

        lenient()
                .when(inventory.getBook())
                .thenReturn(book);

        return inventory;
    }
}