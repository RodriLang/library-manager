package com.rodrilang.librarymanager.catalog.candidate.service;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.catalog.candidate.dto.internal.CatalogCandidateBookResolutionResult;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;
import com.rodrilang.librarymanager.catalog.candidate.repository.CatalogCandidateRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.service.InventoryCountCandidateResolutionService;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogCandidateBookResolutionService {

    private final CatalogCandidateRepository repository;
    private final InventoryCountItemRepository countItemRepository;
    private final BookService bookService;
    private final UserRepository userRepository;
    private final IsbnService isbnService;
    private final BookstoreContext bookstoreContext;
    private final InventoryCountCandidateResolutionService candidateResolutionService;

    @Transactional
    public CatalogCandidateBookResolutionResult resolveWithBook(
            Long candidateId,
            Long bookId
    ) {
        CatalogCandidate candidate = requireAccessibleForUpdate(candidateId);
        Book book = bookService.getEntityById(bookId);

        validateMatchingIsbn(candidate, book);

        return resolve(candidate, book);
    }

    /**
     * Se usa cuando el caller ya tiene una transacción activa y el candidato
     * fue obtenido con bloqueo para actualización.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CatalogCandidateBookResolutionResult resolveLocked(CatalogCandidate candidate, Book book) {
        validateMatchingIsbn(candidate, book);

        return resolve(candidate, book);
    }

    private CatalogCandidateBookResolutionResult resolve(CatalogCandidate candidate, Book book) {
        if (candidate.getStatus() == CatalogCandidateStatus.RESOLVED) {
            if (candidate.getResolvedBook() != null && candidate.getResolvedBook().getId().equals(book.getId())) {
                candidateResolutionService.resolve(candidate.getId(), book.getId());

                return new CatalogCandidateBookResolutionResult(candidate, true);
            }

            throw new BusinessException("El ISBN ya fue resuelto con otro libro del catálogo");
        }

        requirePending(candidate);

        User user = userRepository.findByIdAndEnabledTrueAndAccountLockedFalse(bookstoreContext.getCurrentUserId())
                .orElseThrow(() -> new BusinessException("No se encontró el usuario autenticado"));

        candidate.setResolvedBook(book);
        candidate.setResolvedByUser(user);
        candidate.setResolvedAt(Instant.now());
        candidate.setStatus(CatalogCandidateStatus.RESOLVED);

        CatalogCandidate saved = repository.save(candidate);

        candidateResolutionService.resolve(saved.getId(), book.getId());

        return new CatalogCandidateBookResolutionResult(saved, false);
    }

    private CatalogCandidate requireAccessibleForUpdate(Long candidateId) {
        CatalogCandidate candidate = repository
                .findDetailedByIdForUpdate(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el candidato de catálogo"));

        requireBookstoreAccess(candidate);

        return candidate;
    }

    private void requireBookstoreAccess(CatalogCandidate candidate) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        if (candidate.getStatus() == CatalogCandidateStatus.RESOLVED) {
            if (countItemRepository.existsByCatalogCandidateIdAndSessionBookstoreId(candidate.getId(), bookstoreId)) {
                return;
            }
        } else if (
                countItemRepository.existsOperationalPendingCatalogCandidate(
                        candidate.getId(),
                        bookstoreId,
                        List.of(
                                InventoryCountStatus.OPEN,
                                InventoryCountStatus.REVIEW,
                                InventoryCountStatus.APPLIED_WITH_PENDING
                        )
                )
        ) {
            return;
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
}