package com.rodrilang.librarymanager.catalog.candidate.service;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.catalog.candidate.dto.request.CreateCatalogCandidateBookRequest;
import com.rodrilang.librarymanager.catalog.candidate.dto.response.CatalogCandidateResponse;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;
import com.rodrilang.librarymanager.catalog.candidate.repository.CatalogCandidateRepository;
import com.rodrilang.librarymanager.dto.request.BookRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountPurpose;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.service.InventoryCountCandidateResolutionService;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CatalogCandidateService {

    private final CatalogCandidateRepository repository;
    private final InventoryCountItemRepository countItemRepository;
    private final BookService bookService;
    private final UserRepository userRepository;
    private final IsbnService isbnService;
    private final BookstoreContext bookstoreContext;
    private final InventoryCountCandidateResolutionService candidateResolutionService;

    @Transactional(readOnly = true)
    public Page<CatalogCandidateResponse> findAll(CatalogCandidateStatus status, Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Page<CatalogCandidate> candidates = repository.findOperationalPendingByBookstore(
                bookstoreId,
                status,
                InventoryCountItemStatus.PENDING_CATALOG,
                List.of(
                        InventoryCountStatus.OPEN,
                        InventoryCountStatus.REVIEW,
                        InventoryCountStatus.APPLIED_WITH_PENDING
                ),
                pageable
        );

        Map<Long, PendingInventoryContext> contexts = loadPendingInventoryContexts(
                candidates.getContent().stream().map(CatalogCandidate::getId).toList(),
                bookstoreId
        );

        return candidates.map(candidate -> toResponse(candidate, contexts.get(candidate.getId()), false));
    }

    @Transactional(readOnly = true)
    public CatalogCandidateResponse findById(Long candidateId) {
        CatalogCandidate candidate = requireAccessible(candidateId);
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Map<Long, PendingInventoryContext> contexts = loadPendingInventoryContexts(
                List.of(candidateId),
                bookstoreId
        );
        return toResponse(candidate, contexts.get(candidateId), false);
    }

    @Transactional
    public CatalogCandidateResponse resolveWithBook(Long candidateId, Long bookId) {
        CatalogCandidate candidate = requireAccessibleForUpdate(candidateId);
        Book book = bookService.getEntityById(bookId);
        validateMatchingIsbn(candidate, book);
        return resolve(candidate, book);
    }

    @Transactional
    public CatalogCandidateResponse createBookAndResolve(Long candidateId, CreateCatalogCandidateBookRequest request) {
        CatalogCandidate candidate = requireAccessibleForUpdate(candidateId);

        // Otra librería pudo resolver este mismo ISBN mientras el formulario estaba abierto.
        // Reutilizamos la resolución global y nunca intentamos crear un segundo libro.
        if (candidate.getStatus() == CatalogCandidateStatus.RESOLVED && candidate.getResolvedBook() != null) {
            candidateResolutionService.resolve(candidate.getId(), candidate.getResolvedBook().getId());
            return toResponse(candidate, null, true);
        }

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
                candidateResolutionService.resolve(candidate.getId(), book.getId());
                return toResponse(candidate, null, true);
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
        return toResponse(saved, null, false);
    }

    private CatalogCandidate requireAccessible(Long candidateId) {
        CatalogCandidate candidate = repository.findDetailedById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el candidato de catálogo"));
        requireBookstoreAccess(candidate);
        return candidate;
    }

    private CatalogCandidate requireAccessibleForUpdate(Long candidateId) {
        CatalogCandidate candidate = repository.findDetailedByIdForUpdate(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el candidato de catálogo"));
        requireBookstoreAccess(candidate);
        return candidate;
    }

    private void requireBookstoreAccess(CatalogCandidate candidate) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        // Una resolución ya completada puede ser consultada por cualquier librería que haya
        // tenido ese ISBN en una de sus propias cargas. Esto permite cerrar limpiamente
        // formularios que quedaron abiertos mientras otra librería resolvía el mismo ISBN.
        if (candidate.getStatus() == CatalogCandidateStatus.RESOLVED) {
            if (countItemRepository.existsByCatalogCandidateIdAndSessionBookstoreId(candidate.getId(), bookstoreId)) {
                return;
            }
        } else if (countItemRepository.existsOperationalPendingCatalogCandidate(
                candidate.getId(),
                bookstoreId,
                List.of(
                        InventoryCountStatus.OPEN,
                        InventoryCountStatus.REVIEW,
                        InventoryCountStatus.APPLIED_WITH_PENDING
                )
        )) {
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

    private CatalogCandidateResponse toResponse(
            CatalogCandidate candidate,
            PendingInventoryContext inventoryContext,
            boolean resolutionReused
    ) {
        PendingInventoryContext context = inventoryContext != null
                ? inventoryContext
                : PendingInventoryContext.empty();

        return new CatalogCandidateResponse(
                candidate.getId(),
                candidate.getIsbn10(),
                candidate.getIsbn13(),
                candidate.getStatus(),
                candidate.getResolvedBook() != null ? candidate.getResolvedBook().getId() : null,
                candidate.getResolvedBook() != null ? candidate.getResolvedBook().getTitle() : null,
                context.units(),
                context.sessionIds().size(),
                context.latestSessionId(),
                context.latestPurpose(),
                resolutionReused,
                context.firstDetectedAt(),
                candidate.getResolvedAt()
        );
    }

    private Map<Long, PendingInventoryContext> loadPendingInventoryContexts(
            Collection<Long> candidateIds,
            Long bookstoreId
    ) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PendingInventoryContextBuilder> builders = new HashMap<>();
        countItemRepository.findAllByCatalogCandidateIdIn(candidateIds).stream()
                .filter(item -> item.getAppliedAt() == null)
                .filter(item -> item.getStatus() != InventoryCountItemStatus.SUPERSEDED)
                .filter(item -> isActivePendingSession(item.getSession().getStatus()))
                .filter(item -> bookstoreId == null || item.getSession().getBookstore().getId().equals(bookstoreId))
                .forEach(item -> builders
                        .computeIfAbsent(item.getCatalogCandidate().getId(), ignored -> new PendingInventoryContextBuilder())
                        .add(item));

        Map<Long, PendingInventoryContext> result = new HashMap<>();
        builders.forEach((candidateId, builder) -> result.put(candidateId, builder.build()));
        return result;
    }

    private boolean isActivePendingSession(InventoryCountStatus status) {
        return status == InventoryCountStatus.OPEN
                || status == InventoryCountStatus.REVIEW
                || status == InventoryCountStatus.APPLIED_WITH_PENDING;
    }

    private record PendingInventoryContext(
            int units,
            Set<Long> sessionIds,
            Long latestSessionId,
            InventoryCountPurpose latestPurpose,
            Instant firstDetectedAt
    ) {
        private static PendingInventoryContext empty() {
            return new PendingInventoryContext(0, Set.of(), null, null, null);
        }
    }

    private static final class PendingInventoryContextBuilder {
        private int units;
        private final Set<Long> sessionIds = new HashSet<>();
        private InventoryCountItem latestItem;
        private Instant firstDetectedAt;

        private void add(InventoryCountItem item) {
            units += item.getQuantity();
            sessionIds.add(item.getSession().getId());
            if (item.getFirstScannedAt() != null
                    && (firstDetectedAt == null || item.getFirstScannedAt().isBefore(firstDetectedAt))) {
                firstDetectedAt = item.getFirstScannedAt();
            }
            if (latestItem == null || Comparator
                    .comparing((InventoryCountItem value) -> value.getSession().getCreatedAt())
                    .compare(item, latestItem) > 0) {
                latestItem = item;
            }
        }

        private PendingInventoryContext build() {
            return new PendingInventoryContext(
                    units,
                    Set.copyOf(sessionIds),
                    latestItem != null ? latestItem.getSession().getId() : null,
                    latestItem != null ? latestItem.getSession().getPurpose() : null,
                    firstDetectedAt
            );
        }
    }
}
