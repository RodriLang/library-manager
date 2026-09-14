package com.rodrilang.librarymanager.catalog.candidate.service;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.catalog.candidate.dto.request.CreateCatalogCandidateBookRequest;
import com.rodrilang.librarymanager.catalog.candidate.dto.response.CatalogCandidateResponse;
import com.rodrilang.librarymanager.catalog.candidate.event.CatalogCandidateResolvedEvent;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;
import com.rodrilang.librarymanager.catalog.candidate.repository.CatalogCandidateRepository;
import com.rodrilang.librarymanager.dto.request.BookRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CatalogCandidateService {

    private final CatalogCandidateRepository repository;
    private final InventoryCountItemRepository countItemRepository;
    private final BookService bookService;
    private final UserRepository userRepository;
    private final IsbnService isbnService;
    private final BookstoreContext bookstoreContext;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<CatalogCandidateResponse> findAll(CatalogCandidateStatus status, Pageable pageable) {
        Page<CatalogCandidate> candidates;

        if (isAdmin()) {
            candidates = repository.findDetailed(status, pageable);
        } else {
            candidates = repository.findVisibleByBookstore(bookstoreContext.getCurrentBookstoreId(), status, pageable);
        }

        return candidates.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CatalogCandidateResponse findById(Long candidateId) {
        return toResponse(requireAccessible(candidateId));
    }

    @Transactional
    public CatalogCandidateResponse resolveWithBook(Long candidateId, Long bookId) {
        CatalogCandidate candidate = requireAccessible(candidateId);
        Book book = bookService.getEntityById(bookId);
        validateMatchingIsbn(candidate, book);
        return resolve(candidate, book);
    }

    @Transactional
    public CatalogCandidateResponse createBookAndResolve(Long candidateId, CreateCatalogCandidateBookRequest request) {
        CatalogCandidate candidate = requireAccessible(candidateId);
        requirePending(candidate);

        BookDetailResponse created = bookService.create(new BookRequest(
                candidate.getIsbn13(),
                false,
                request.title(),
                request.subtitle(),
                request.description(),
                request.language(),
                request.publicationYear(),
                request.publicationMonth(),
                request.coverUrl(),
                request.categoryName(),
                request.genreName(),
                request.pageCount(),
                request.weightGrams(),
                request.widthCm(),
                request.heightCm(),
                request.depthCm(),
                request.publisherId(),
                request.authorIds()
        ));

        return resolve(candidate, bookService.getEntityById(created.id()));
    }

    private CatalogCandidateResponse resolve(CatalogCandidate candidate, Book book) {
        if (candidate.getStatus() == CatalogCandidateStatus.RESOLVED) {
            if (candidate.getResolvedBook() != null && candidate.getResolvedBook().getId().equals(book.getId())) {
                eventPublisher.publishEvent(new CatalogCandidateResolvedEvent(candidate.getId(), book.getId()));
                return toResponse(candidate);
            }
            throw new BusinessException("El candidato ya fue resuelto con otro libro");
        }

        requirePending(candidate);
        User user = userRepository.findByIdAndEnabledTrueAndAccountLockedFalse(bookstoreContext.getCurrentUserId())
                .orElseThrow(() -> new BusinessException("No se encontró el usuario autenticado"));

        candidate.setResolvedBook(book);
        candidate.setResolvedByUser(user);
        candidate.setResolvedAt(Instant.now());
        candidate.setStatus(CatalogCandidateStatus.RESOLVED);

        CatalogCandidate saved = repository.save(candidate);
        eventPublisher.publishEvent(new CatalogCandidateResolvedEvent(saved.getId(), book.getId()));
        return toResponse(saved);
    }

    private CatalogCandidate requireAccessible(Long candidateId) {
        CatalogCandidate candidate = repository.findDetailedById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el candidato de catálogo"));

        if (isAdmin()
                || candidate.getFirstSeenByBookstore().getId().equals(bookstoreContext.getCurrentBookstoreId())
                || countItemRepository.existsByCatalogCandidateIdAndSessionBookstoreId(
                        candidateId,
                        bookstoreContext.getCurrentBookstoreId()
                )) {
            return candidate;
        }

        throw new ResourceNotFoundException("No se encontró el candidato de catálogo");
    }

    private void validateMatchingIsbn(CatalogCandidate candidate, Book book) {
        ParsedIsbn bookIsbn = isbnService.parse(book.getPreferredIsbn());
        if (!bookIsbn.valid() || !candidate.getIsbn13().equals(bookIsbn.isbn13())) {
            throw new BusinessException("El ISBN del libro seleccionado no coincide con el candidato");
        }
    }

    private void requirePending(CatalogCandidate candidate) {
        if (candidate.getStatus() != CatalogCandidateStatus.PENDING) {
            throw new BusinessException("El candidato no se encuentra pendiente de resolución");
        }
    }

    private boolean isAdmin() {
        return bookstoreContext.getCurrentUser().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private CatalogCandidateResponse toResponse(CatalogCandidate candidate) {
        return new CatalogCandidateResponse(
                candidate.getId(),
                candidate.getIsbn10(),
                candidate.getIsbn13(),
                candidate.getStatus(),
                candidate.getResolvedBook() != null ? candidate.getResolvedBook().getId() : null,
                candidate.getResolvedBook() != null ? candidate.getResolvedBook().getTitle() : null,
                candidate.getFirstSeenByBookstore().getId(),
                candidate.getResolvedAt(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt()
        );
    }
}
