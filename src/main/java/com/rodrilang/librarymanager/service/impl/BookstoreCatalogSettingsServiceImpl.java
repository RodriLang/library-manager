package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.dto.response.PublisherConfigurationDetailResponse;
import com.rodrilang.librarymanager.dto.response.PublisherConfigurationResponse;
import com.rodrilang.librarymanager.enums.PublisherCatalogSort;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.mapper.BookMapper;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.BookstoreExcludedPublisher;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.BookstoreExcludedPublisherRepository;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.repository.projection.PublisherCatalogConfigurationProjection;
import com.rodrilang.librarymanager.repository.projection.PublisherConfigurationDetailProjection;
import com.rodrilang.librarymanager.service.BookstoreCatalogSettingsService;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookstoreCatalogSettingsServiceImpl implements BookstoreCatalogSettingsService {

    private final BookstoreContext bookstoreContext;
    private final PublisherRepository publisherRepository;
    private final BookstoreRepository bookstoreRepository;
    private final BookstoreExcludedPublisherRepository excludedPublisherRepository;
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final EditorialPriceService editorialPriceService;

    @Override
    @Transactional(readOnly = true)
    public Page<PublisherConfigurationResponse> searchPublishers(
            String query,
            Boolean excluded,
            PublisherCatalogSort sort,
            Pageable pageable
    ) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        return publisherRepository
                .searchForCatalogConfiguration(bookstoreId, query, excluded, resolveSort(sort), pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PublisherConfigurationDetailResponse getPublisher(Long publisherId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PublisherConfigurationDetailProjection projection = publisherRepository
                .findCatalogConfigurationDetail(bookstoreId, publisherId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la editorial indicada."));

        return new PublisherConfigurationDetailResponse(
                projection.getId(),
                projection.getName(),
                projection.getBookCount(),
                projection.getExcluded()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookSummaryResponse> getPublisherBooks(Long publisherId, String query, Pageable pageable) {
        if (!publisherRepository.existsById(publisherId)) {
            throw new ResourceNotFoundException("No se encontró la editorial indicada.");
        }

        Page<Book> books;

        if (query == null || query.isBlank()) {
            books = bookRepository.findAllByPublisherForCatalogSettings(publisherId, pageable);
        } else {
            books = bookRepository.searchByPublisherForCatalogSettings(publisherId, query.trim(), pageable);
        }

        return toBookSummaryResponsePage(books);
    }

    @Override
    @Transactional
    public void updatePublisherExclusion(Long publisherId, boolean excluded) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        if (excluded) {
            excludePublisher(bookstoreId, publisherId);
            return;
        }

        includePublisher(bookstoreId, publisherId);
    }

    private void excludePublisher(Long bookstoreId, Long publisherId) {
        if (excludedPublisherRepository.existsByBookstoreIdAndPublisherId(bookstoreId, publisherId)) {
            return;
        }

        Publisher publisher = publisherRepository.findById(publisherId)
                .orElseThrow(() -> new BusinessException("La editorial indicada no existe."));

        Bookstore bookstore = bookstoreRepository.getReferenceById(bookstoreId);

        BookstoreExcludedPublisher exclusion = BookstoreExcludedPublisher.builder()
                .bookstore(bookstore)
                .publisher(publisher)
                .build();

        excludedPublisherRepository.save(exclusion);
    }

    private void includePublisher(Long bookstoreId, Long publisherId) {
        excludedPublisherRepository.deleteByBookstoreIdAndPublisherId(bookstoreId, publisherId);
    }

    private PublisherConfigurationResponse toResponse(PublisherCatalogConfigurationProjection projection) {
        return new PublisherConfigurationResponse(
                projection.getId(),
                projection.getName(),
                projection.getBookCount(),
                projection.getExcluded()
        );
    }

    private String resolveSort(PublisherCatalogSort sort) {
        PublisherCatalogSort resolvedSort = sort != null ? sort : PublisherCatalogSort.BOOK_COUNT_DESC;

        return switch (resolvedSort) {
            case NAME_ASC -> "nameAsc";
            case NAME_DESC -> "nameDesc";
            case BOOK_COUNT_ASC -> "bookCountAsc";
            case BOOK_COUNT_DESC -> "bookCountDesc";
        };
    }

    private Page<BookSummaryResponse> toBookSummaryResponsePage(Page<Book> books) {
        List<Long> bookIds = books.getContent()
                .stream()
                .map(Book::getId)
                .toList();

        Map<Long, EditorialPrice> pricesByBookId = editorialPriceService.findCurrentByBookIds(bookIds);

        return books.map(book ->
                bookMapper.toSummaryResponse(
                        book,
                        pricesByBookId.get(book.getId())
                )
        );
    }
}