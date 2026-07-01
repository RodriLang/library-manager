package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.request.BookRequest;
import com.rodrilang.librarymanager.dto.request.UpdateBookRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.mapper.BookMapper;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.service.AuthorService;
import com.rodrilang.librarymanager.service.BookCatalogService;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.PublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final PublisherService publisherService;
    private final AuthorService authorService;
    private final BookCatalogService bookCatalogService;

    @Transactional
    @Override
    public BookDetailResponse create(BookRequest request) {

        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateResourceException("ISBN ya registrado");
        }

        Publisher publisher = publisherService.getEntityById(request.publisherId());
        Set<Author> authors = authorService.getEntitiesByIds(request.authorIds());

        Book book = bookMapper.toEntity(request);
        book.setPublisher(publisher);
        book.setAuthors(authors);
        book.setSource(BookSource.MANUAL);

        try {
            Book saved = bookRepository.save(book);
            return bookMapper.toDetailResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("ISBN ya registrado");
        }
    }

    @Override
    public BookDetailResponse getById(Long id) {

        return bookMapper.toDetailResponse(getEntityById(id));
    }

    @Override
    public BookDetailResponse getByIsbn(String isbn) {

        return bookMapper.toDetailResponse(getEntityByIsbn(isbn));
    }

    @Override
    @Transactional
    public BookDetailResponse lookupByIsbn(String isbn) {
        Book book = bookCatalogService.getOrCreateByIsbn(isbn);

        return bookMapper.toDetailResponse(book);
    }

    @Transactional
    @Override
    public BookDetailResponse update(Long bookId, UpdateBookRequest request) {

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

        return bookMapper.toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<BookSummaryResponse> search(String query, Pageable pageable) {

        if (query == null || query.isBlank()) {
            return Page.empty(pageable);
        }

        return bookRepository.search(query, pageable)
                .map(bookMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<BookSummaryResponse> getAll(Pageable pageable) {
        return bookRepository.findAll(pageable).map(bookMapper::toSummaryResponse);
    }

    @Override
    public Book getEntityById(Long id) {

        return bookRepository.findById(id)
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
}
