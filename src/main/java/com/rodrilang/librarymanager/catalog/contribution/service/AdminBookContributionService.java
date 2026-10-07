package com.rodrilang.librarymanager.catalog.contribution.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.catalog.contribution.dto.BookFieldProposalResponse;
import com.rodrilang.librarymanager.catalog.contribution.dto.ReviewBookFieldProposalRequest;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldProposalStatus;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldSource;
import com.rodrilang.librarymanager.catalog.contribution.enums.ProposalReviewDecision;
import com.rodrilang.librarymanager.catalog.contribution.model.BookFieldProposal;
import com.rodrilang.librarymanager.catalog.contribution.repository.BookFieldProposalRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.event.BookPublicationChangedEvent;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminBookContributionService {

    private final BookFieldProposalRepository proposalRepository;
    private final BookFieldValueService fieldValueService;
    private final BookstoreBookFieldOverrideService overrideService;
    private final BookRepository bookRepository;
    private final BookstoreContext bookstoreContext;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<BookFieldProposalResponse> list(
            BookFieldProposalStatus status,
            Long bookId,
            BookField field,
            Pageable pageable
    ) {
        return proposalRepository.search(status, bookId, field, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public BookFieldProposalResponse review(Long proposalId, ReviewBookFieldProposalRequest request) {
        BookFieldProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el aporte solicitado."));

        if (proposal.getStatus() != BookFieldProposalStatus.PENDING) {
            throw new BusinessException("El aporte ya fue revisado.");
        }

        Long reviewerUserId = bookstoreContext.getCurrentUserId();
        Instant reviewedAt = Instant.now();

        if (request.decision() == ProposalReviewDecision.APPROVE) {
            Book book = proposal.getBook();
            if (proposal.getField() == BookField.PUBLICATION_MONTH && book.getPublicationYear() == null) {
                throw new BusinessException("Verificá primero el año de publicación antes de aprobar el mes.");
            }
            fieldValueService.applySerialized(book, proposal.getField(), proposal.getProposedValue());
            book.setFieldSource(proposal.getField(), BookFieldSource.VERIFIED);
            bookRepository.save(book);
            overrideService.removeRedundantOverrides(
                    book.getId(),
                    proposal.getField(),
                    proposal.getProposedValue()
            );

            proposal.setStatus(BookFieldProposalStatus.APPROVED);
            proposalRepository.supersedeOtherPending(
                    book.getId(),
                    proposal.getField(),
                    proposal.getId(),
                    reviewedAt,
                    reviewerUserId,
                    BookFieldProposalStatus.PENDING,
                    BookFieldProposalStatus.SUPERSEDED
            );
            eventPublisher.publishEvent(new BookPublicationChangedEvent(book.getId()));
        } else {
            Book book = proposal.getBook();
            if (proposal.getCurrentValue() == null
                    && fieldValueService.equivalent(book, proposal.getField(), proposal.getProposedValue())
                    && book.getFieldMetadata() != null
                    && BookFieldSource.STORE.name().equals(book.getFieldMetadata().get(proposal.getField().key()))) {
                overrideService.upsert(
                        book,
                        proposal.getField(),
                        proposal.getProposedValue(),
                        proposal.getSubmittedByBookstoreId(),
                        proposal.getSubmittedByUserId()
                );
                fieldValueService.clear(book, proposal.getField());
                book.setFieldSource(proposal.getField(), null);
                bookRepository.save(book);
                eventPublisher.publishEvent(new BookPublicationChangedEvent(book.getId()));
            }
            proposal.setStatus(BookFieldProposalStatus.REJECTED);
        }

        proposal.setReviewedAt(reviewedAt);
        proposal.setReviewedByUserId(reviewerUserId);
        return toResponse(proposalRepository.save(proposal));
    }

    private BookFieldProposalResponse toResponse(BookFieldProposal proposal) {
        Book book = proposal.getBook();
        return new BookFieldProposalResponse(
                proposal.getId(),
                book.getId(),
                book.getTitle(),
                book.getPreferredIsbn(),
                proposal.getField(),
                proposal.getCurrentValue(),
                proposal.getProposedValue(),
                fieldValueService.displaySerialized(proposal.getField(), proposal.getCurrentValue()),
                fieldValueService.displaySerialized(proposal.getField(), proposal.getProposedValue()),
                proposal.getSubmittedByBookstoreId(),
                proposal.getSubmittedByUserId(),
                proposal.getStatus(),
                proposal.getCreatedAt(),
                proposal.getReviewedAt(),
                proposal.getReviewedByUserId()
        );
    }
}
