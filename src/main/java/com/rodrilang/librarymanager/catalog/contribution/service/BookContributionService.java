package com.rodrilang.librarymanager.catalog.contribution.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.catalog.contribution.dto.BookContributionRequest;
import com.rodrilang.librarymanager.catalog.contribution.dto.BookContributionResponse;
import com.rodrilang.librarymanager.catalog.contribution.dto.ContributionFieldResult;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldProposalStatus;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldSource;
import com.rodrilang.librarymanager.catalog.contribution.enums.ContributionAction;
import com.rodrilang.librarymanager.catalog.contribution.model.BookFieldProposal;
import com.rodrilang.librarymanager.catalog.contribution.repository.BookFieldProposalRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.event.BookPublicationChangedEvent;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.service.AuthorService;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.PublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookContributionService {

    private final BookService bookService;
    private final BookRepository bookRepository;
    private final BookFieldProposalRepository proposalRepository;
    private final BookFieldValueService fieldValueService;
    private final BookstoreContext bookstoreContext;
    private final PublisherService publisherService;
    private final AuthorService authorService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public BookContributionResponse contribute(Long bookId, BookContributionRequest request) {
        Book book = bookService.getEntityById(bookId);
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Long userId = bookstoreContext.getCurrentUserId();

        Map<BookField, Object> values = collectValues(request);
        if (values.isEmpty()) {
            throw new BusinessException("Informá al menos un dato bibliográfico para contribuir.");
        }

        validateReferences(book, request);

        List<ContributionFieldResult> results = new ArrayList<>();
        boolean changed = false;

        for (Map.Entry<BookField, Object> entry : values.entrySet()) {
            BookField field = entry.getKey();
            String proposedValue = fieldValueService.serializeProposed(field, entry.getValue());
            if (proposedValue == null) {
                continue;
            }

            if (fieldValueService.equivalent(book, field, proposedValue)) {
                results.add(new ContributionFieldResult(field, ContributionAction.UNCHANGED, null));
                continue;
            }

            if (!fieldValueService.hasValue(book, field)) {
                fieldValueService.applySerialized(book, field, proposedValue);
                book.setFieldSource(field, BookFieldSource.STORE);
                Long proposalId = createProposalIfNeeded(
                        book,
                        field,
                        null,
                        proposedValue,
                        bookstoreId,
                        userId
                );
                results.add(new ContributionFieldResult(field, ContributionAction.APPLIED, proposalId));
                changed = true;
                continue;
            }

            Long proposalId = createProposalIfNeeded(
                    book,
                    field,
                    fieldValueService.serializeCurrent(book, field),
                    proposedValue,
                    bookstoreId,
                    userId
            );
            results.add(new ContributionFieldResult(field, ContributionAction.PROPOSED, proposalId));

        }

        if (results.isEmpty()) {
            throw new BusinessException("No se encontraron datos válidos para aportar.");
        }

        if (changed) {
            bookRepository.save(book);
            eventPublisher.publishEvent(new BookPublicationChangedEvent(book.getId()));
        }

        return new BookContributionResponse(bookService.getById(bookId), results);
    }

    private Long createProposalIfNeeded(
            Book book,
            BookField field,
            String currentValue,
            String proposedValue,
            Long bookstoreId,
            Long userId
    ) {
        boolean alreadyPending = proposalRepository
                .existsByBook_IdAndFieldAndSubmittedByBookstoreIdAndStatusAndProposedValue(
                        book.getId(), field, bookstoreId, BookFieldProposalStatus.PENDING, proposedValue
                );

        if (alreadyPending) {
            return null;
        }

        BookFieldProposal proposal = BookFieldProposal.builder()
                .book(book)
                .field(field)
                .currentValue(currentValue)
                .proposedValue(proposedValue)
                .submittedByBookstoreId(bookstoreId)
                .submittedByUserId(userId)
                .status(BookFieldProposalStatus.PENDING)
                .build();

        return proposalRepository.save(proposal).getId();
    }

    private void validateReferences(Book book, BookContributionRequest request) {
        if (request.publisherId() != null) {
            publisherService.getEntityById(request.publisherId());
        }
        if (request.authorIds() != null && !request.authorIds().isEmpty()) {
            authorService.getEntitiesByIds(request.authorIds());
        }
        if (request.publicationMonth() != null
                && request.publicationYear() == null
                && book.getPublicationYear() == null) {
            throw new BusinessException("El mes de publicación requiere un año de publicación.");
        }
    }

    private Map<BookField, Object> collectValues(BookContributionRequest request) {
        Map<BookField, Object> values = new LinkedHashMap<>();
        putText(values, BookField.TITLE, request.title());
        putText(values, BookField.SUBTITLE, request.subtitle());
        putText(values, BookField.DESCRIPTION, request.description());
        putText(values, BookField.LANGUAGE, request.language());
        put(values, BookField.PAGE_COUNT, request.pageCount());
        put(values, BookField.PUBLICATION_YEAR, request.publicationYear());
        put(values, BookField.PUBLICATION_MONTH, request.publicationMonth());
        putText(values, BookField.COVER_URL, request.coverUrl());
        putText(values, BookField.CATEGORY_NAME, request.categoryName());
        putText(values, BookField.GENRE_NAME, request.genreName());
        put(values, BookField.PUBLISHER, request.publisherId());
        if (request.authorIds() != null && !request.authorIds().isEmpty()) {
            values.put(BookField.AUTHORS, request.authorIds());
        }
        put(values, BookField.WEIGHT_GRAMS, request.weightGrams());
        put(values, BookField.WIDTH_CM, request.widthCm());
        put(values, BookField.HEIGHT_CM, request.heightCm());
        put(values, BookField.DEPTH_CM, request.depthCm());
        return values;
    }

    private void putText(Map<BookField, Object> values, BookField field, String value) {
        if (value != null && !value.isBlank()) {
            values.put(field, value.trim());
        }
    }

    private void put(Map<BookField, Object> values, BookField field, Object value) {
        if (value != null) {
            values.put(field, value);
        }
    }
}
