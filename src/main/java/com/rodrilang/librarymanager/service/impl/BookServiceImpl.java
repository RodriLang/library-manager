package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.dto.request.BookRequest;
import com.rodrilang.librarymanager.dto.request.UpdateBookRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.mapper.BookMapper;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.service.AuthorService;
import com.rodrilang.librarymanager.service.BookCatalogService;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.BookstoreService;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import com.rodrilang.librarymanager.service.PublisherService;
import com.rodrilang.librarymanager.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private static final Map<String, String> BOOK_SORT_MAPPING = Map.of("title", "titleSort");

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final PublisherService publisherService;
    private final AuthorService authorService;
    private final BookCatalogService bookCatalogService;
    private final EditorialPriceService editorialPriceService;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;
    private final IsbnService isbnService;

    @Transactional
    @Override
    public BookDetailResponse create(BookRequest request) {
        ParsedIsbn parsedIsbn = parseRequiredIsbn(request.isbn());

        if (existsByParsedIsbn(parsedIsbn)) {
            throw new DuplicateResourceException("ISBN ya registrado");
        }

        Publisher publisher = publisherService.getEntityById(request.publisherId());
        Set<Author> authors = authorService.getEntitiesByIds(request.authorIds());
        Bookstore bookstore = bookstoreService.getEntityById(bookstoreContext.getCurrentBookstoreId());

        Book book = bookMapper.toEntity(request);
        book.setIsbn10(parsedIsbn.isbn10());
        book.setIsbn13(parsedIsbn.isbn13());
        book.setPublisher(publisher);
        book.setAuthors(authors);
        book.setSource(BookSource.MANUAL);
        book.setCatalogStatus(BookCatalogStatus.PENDING_REVIEW);
        book.setCreatedByBookstore(bookstore);
        book.setActive(true);

        try {
            Book saved = bookRepository.save(book);
            return toDetailResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            log.error("Error de integridad al crear libro. isbn={}", parsedIsbn.preferredIsbn(), ex);
            throw new BusinessException("No se pudo registrar el libro. Verifique los datos ingresados.");
        }
    }

    @Override
    public BookDetailResponse getById(Long id) {
        return toDetailResponse(getEntityById(id));
    }

    @Override
    public BookDetailResponse getByIsbn(String isbn) {
        return toDetailResponse(getEntityByIsbn(isbn));
    }

    @Transactional
    @Override
    public BookDetailResponse lookupByIsbn(String isbn) {
        return toDetailResponse(bookCatalogService.getOrCreateByIsbn(isbn));
    }

    @Transactional
    @Override
    public BookDetailResponse update(Long bookId, UpdateBookRequest request) {
        Book book = getEntityById(bookId);

        bookMapper.updateEntity(request, book);

        if (request.publisherId() != null) {
            book.setPublisher(publisherService.getEntityById(request.publisherId()));
        }

        if (request.authorIds() != null) {
            book.setAuthors(authorService.getEntitiesByIds(request.authorIds()));
        }

        return toDetailResponse(bookRepository.save(book));
    }

    @Transactional(readOnly = true)
    @Override
    public Page<BookSummaryResponse> search(
            String query,
            boolean force,
            Pageable pageable
    ) {
        if (query == null || query.isBlank()) {
            return Page.empty(pageable);
        }

        String normalizedQuery = query.trim();

        boolean identifierQuery =
                normalizedQuery.matches("[0-9Xx\\-\\s]+");

        if (!force) {
            int minimumLength = identifierQuery ? 8 : 3;

            if (normalizedQuery.length() < minimumLength) {
                return Page.empty(pageable);
            }
        }

        long repositoryStart = System.currentTimeMillis();

        Page<Book> books;

        if (identifierQuery) {
            String normalizedIdentifier =
                    normalizeSearchIdentifier(normalizedQuery);

            if (normalizedIdentifier == null) {
                return Page.empty(pageable);
            }

            books = bookRepository.searchByIsbn(
                    normalizedIdentifier,
                    pageable
            );
        } else {
            books = bookRepository.searchText(
                    normalizedQuery,
                    pageable
            );
        }

        long repositoryTime =
                System.currentTimeMillis() - repositoryStart;

        long mappingStart = System.currentTimeMillis();

        Page<BookSummaryResponse> response =
                books.map(this::toSummaryResponse);

        long mappingTime =
                System.currentTimeMillis() - mappingStart;

        log.info(
                "Book search timing. query={} repositoryTime={}ms mappingTime={}ms results={} totalElements={}",
                normalizedQuery,
                repositoryTime,
                mappingTime,
                books.getNumberOfElements(),
                books.getTotalElements()
        );

        return response;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<BookSummaryResponse> getAll(Pageable pageable) {
        if (PageableUtils.hasSort(pageable, "editorialPrice")) {
            boolean ascending = PageableUtils.isAscending(pageable, "editorialPrice");
            Pageable unsortedPageable = PageableUtils.withoutSort(pageable);

            Page<Book> books = ascending
                    ? bookRepository.findAllOrderByCurrentEditorialPriceAsc(unsortedPageable)
                    : bookRepository.findAllOrderByCurrentEditorialPriceDesc(unsortedPageable);

            return books.map(this::toSummaryResponse);
        }

        Pageable normalizedPageable = PageableUtils.mapSortProperties(pageable, BOOK_SORT_MAPPING);
        return bookRepository.findAll(normalizedPageable).map(this::toSummaryResponse);
    }

    @Override
    public Book getEntityById(Long id) {
        return bookRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un libro con el ID: " + id));
    }

    @Override
    public Book getEntityByIsbn(String isbn) {
        ParsedIsbn parsedIsbn = parseRequiredIsbn(isbn);
        Book book = findByParsedIsbnWithDetails(parsedIsbn);

        if (book == null) {
            throw new ResourceNotFoundException("No se encontró un libro con el ISBN: " + parsedIsbn.preferredIsbn());
        }

        return book;
    }

    @Override
    public boolean existsById(Long bookId) {
        return bookRepository.existsById(bookId);
    }

    @Override
    public boolean existsByIsbn(String isbn) {
        ParsedIsbn parsedIsbn = isbnService.parse(isbn);
        return parsedIsbn.valid() && existsByParsedIsbn(parsedIsbn);
    }

    private ParsedIsbn parseRequiredIsbn(String isbn) {
        ParsedIsbn parsedIsbn = isbnService.parse(isbn);

        if (!parsedIsbn.valid()) {
            throw new BusinessException("El ISBN ingresado no es válido.");
        }

        return parsedIsbn;
    }

    private boolean existsByParsedIsbn(ParsedIsbn parsedIsbn) {
        if (bookRepository.existsByIsbn13(parsedIsbn.isbn13())) {
            return true;
        }

        return parsedIsbn.isbn10() != null && bookRepository.existsByIsbn10(parsedIsbn.isbn10());
    }

    private Book findByParsedIsbnWithDetails(ParsedIsbn parsedIsbn) {
        Book byIsbn13 = bookRepository.findByIsbn13WithDetails(parsedIsbn.isbn13()).orElse(null);

        if (byIsbn13 != null || parsedIsbn.isbn10() == null) {
            return byIsbn13;
        }

        return bookRepository.findByIsbn10WithDetails(parsedIsbn.isbn10()).orElse(null);
    }

    private String normalizeSearchIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value
                .trim()
                .toUpperCase()
                .replaceAll("[^0-9X]", "");

        return normalized.isBlank()
                ? null
                : normalized;
    }

    private BookSummaryResponse toSummaryResponse(Book book) {
        EditorialPrice editorialPrice = editorialPriceService.findCurrentByBookId(book.getId()).orElse(null);
        return bookMapper.toSummaryResponse(book, editorialPrice);
    }

    private BookDetailResponse toDetailResponse(Book book) {
        EditorialPrice editorialPrice = editorialPriceService.findCurrentByBookId(book.getId()).orElse(null);
        return bookMapper.toDetailResponse(book, editorialPrice);
    }
}