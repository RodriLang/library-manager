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
import com.rodrilang.librarymanager.util.IsbnUtils;
import com.rodrilang.librarymanager.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final PublisherService publisherService;
    private final AuthorService authorService;
    private final BookCatalogService bookCatalogService;
    private final EditorialPriceService editorialPriceService;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;

    private static final Map<String, String> BOOK_SORT_MAPPING = Map.of("title", "titleSort");

    @Transactional
    @Override
    public BookDetailResponse create(BookRequest request) {

        String normalizedIsbn = IsbnUtils.normalize(request.isbn());

        if (bookRepository.existsByIsbn(normalizedIsbn)) {
            throw new DuplicateResourceException("ISBN ya registrado");
        }

        Publisher publisher = publisherService.getEntityById(request.publisherId());
        Set<Author> authors = authorService.getEntitiesByIds(request.authorIds());
        Bookstore bookstore = bookstoreService.getEntityById(bookstoreContext.getCurrentBookstoreId());

        Book book = bookMapper.toEntity(request);
        book.setIsbn(normalizedIsbn);
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
            log.error("Error de integridad al crear libro. isbn={}", normalizedIsbn, ex);
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

    @Override
    @Transactional
    public BookDetailResponse lookupByIsbn(String isbn) {
        Book book = bookCatalogService.getOrCreateByIsbn(isbn);

        return toDetailResponse(book);
    }

    @Transactional
    @Override
    public BookDetailResponse update(Long bookId, UpdateBookRequest request) {

        log.info("REQUEST coverUrl: {}", request.coverUrl());
        Book book = getEntityById(bookId);

        bookMapper.updateEntity(request, book);

        if (request.publisherId() != null) {
            Publisher publisher = publisherService.getEntityById(request.publisherId());
            book.setPublisher(publisher);
        }

        if (request.authorIds() != null) {
            Set<Author> authors = authorService.getEntitiesByIds(request.authorIds());
            book.setAuthors(authors);
        }

        Book saved = bookRepository.save(book);

        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<BookSummaryResponse> search(String query, Pageable pageable) {

        if (query == null || query.isBlank()) {
            return Page.empty(pageable);
        }

        return bookRepository.search(query, pageable)
                .map(this::toSummaryResponse);
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

        return bookRepository.findAll(normalizedPageable)
                .map(this::toSummaryResponse);
    }

    @Override
    public Book getEntityById(Long id) {

        return bookRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un libro con el ID: " + id));
    }

    @Override
    public Book getEntityByIsbn(String isbn) {
        return bookRepository.findByIsbnWithDetails(isbn)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un libro con el ISBN: " + isbn));
    }

    @Override
    public boolean existsById(Long bookId) {
        return bookRepository.existsById(bookId);
    }

    @Override
    public boolean existsByIsbn(String isbn) {
        return bookRepository.existsByIsbn(isbn);
    }

    private BookSummaryResponse toSummaryResponse(Book book) {
        EditorialPrice editorialPrice = editorialPriceService.findCurrentByBookId(book.getId())
                .orElse(null);

        return bookMapper.toSummaryResponse(book, editorialPrice);
    }

    private BookDetailResponse toDetailResponse(Book book) {
        EditorialPrice editorialPrice = editorialPriceService.findCurrentByBookId(book.getId())
                .orElse(null);

        return bookMapper.toDetailResponse(book, editorialPrice);
    }
}
