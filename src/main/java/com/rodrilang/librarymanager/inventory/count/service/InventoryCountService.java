package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.count.dto.request.CreateInventoryCountRequest;
import com.rodrilang.librarymanager.inventory.count.dto.request.UpdateInventoryCountConfigurationRequest;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountItemResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountReportSummaryResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountResultResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountSessionResponse;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountDifferenceType;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountPurpose;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountResultRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountSessionRepository;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;

import java.math.BigDecimal;

import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryCountService {

    private final InventoryCountSessionRepository sessionRepository;
    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountResultRepository resultRepository;
    private final InventoryCountSessionAccessService accessService;
    private final InventoryCountSnapshotService snapshotService;
    private final InventoryCountReviewService reviewService;
    private final InventoryCountApplyService applyService;
    private final InventoryCountRevertService revertService;
    private final InventoryCountConfigurationService configurationService;
    private final InventoryCountReportService reportService;
    private final InventoryCountResponseMapper responseMapper;
    private final InventoryCountPriceResolver priceResolver;
    private final BookstoreService bookstoreService;
    private final UserRepository userRepository;
    private final BookstoreContext bookstoreContext;

    @Transactional
    public InventoryCountSessionResponse create(CreateInventoryCountRequest request) {
        validateModePurpose(request.mode(), request.purpose());

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookCondition condition = request.condition() != null ? request.condition() : BookCondition.NEW;

        if (request.mode() == InventoryCountMode.ABSOLUTE && sessionRepository.existsByBookstoreIdAndConditionAndModeAndStatusIn(
                bookstoreId,
                condition,
                InventoryCountMode.ABSOLUTE,
                List.of(InventoryCountStatus.OPEN, InventoryCountStatus.REVIEW)
        )) {
            throw new BusinessException("Ya existe un conteo absoluto abierto para esta condición de inventario");
        }

        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);
        User user = userRepository.findByIdAndEnabledTrueAndAccountLockedFalse(bookstoreContext.getCurrentUserId())
                .orElseThrow(() -> new BusinessException("No se encontró el usuario autenticado"));

        InventoryCountSession session = sessionRepository.save(InventoryCountSession.builder()
                .bookstore(bookstore)
                .createdByUser(user)
                .mode(request.mode())
                .purpose(request.purpose())
                .condition(condition)
                .status(InventoryCountStatus.OPEN)
                .baselineAt(Instant.now())
                .notes(normalize(request.notes()))
                .build());

        snapshotService.createBaseline(session);
        return responseMapper.toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public InventoryCountSessionResponse findById(Long sessionId) {
        return responseMapper.toSessionResponse(accessService.require(sessionId));
    }

    @Transactional(readOnly = true)
    public Page<InventoryCountSessionResponse> findAll(Pageable pageable) {
        return sessionRepository.findAllByBookstoreId(bookstoreContext.getCurrentBookstoreId(), pageable)
                .map(responseMapper::toSessionResponse);
    }

    @Transactional(readOnly = true)
    public Optional<InventoryCountSessionResponse> findActiveAbsolute(BookCondition condition) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        return sessionRepository
                .findFirstByBookstoreIdAndConditionAndModeAndStatusInOrderByCreatedAtDesc(
                        bookstoreId,
                        condition,
                        InventoryCountMode.ABSOLUTE,
                        List.of(
                                InventoryCountStatus.OPEN,
                                InventoryCountStatus.REVIEW
                        )
                )
                .map(responseMapper::toSessionResponse);
    }

    @Transactional(readOnly = true)
    public Page<InventoryCountItemResponse> findItems(
            Long sessionId,
            InventoryCountItemStatus status,
            String query,
            Pageable pageable
    ) {
        InventoryCountSession session = accessService.require(sessionId);
        String searchQuery = normalizeSearchQuery(query);
        String identifierQuery = normalizeIdentifierQuery(query);

        Page<com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem> page;

        if (searchQuery == null) {
            page = status == null
                    ? itemRepository.findAllBySessionId(sessionId, pageable)
                    : itemRepository.findAllBySessionIdAndStatus(sessionId, status, pageable);
        } else {
            boolean searchIdentifier = identifierQuery != null;

            page = itemRepository.search(
                    sessionId,
                    status,
                    searchQuery,
                    searchIdentifier,
                    searchIdentifier ? identifierQuery : "",
                    pageable
            );
        }

        List<Long> bookIds = page.getContent().stream()
                .filter(item -> item.getBook() != null)
                .map(item -> item.getBook().getId())
                .distinct()
                .toList();
        Map<Long, Inventory> inventories = priceResolver.existingInventories(session, bookIds);
        Map<Long, BigDecimal> editorialPrices = priceResolver.currentEditorialPrices(bookIds);

        return page.map(item -> {
            if (item.getBook() == null) {
                return responseMapper.toItemResponse(item, null, null);
            }
            Long bookId = item.getBook().getId();
            return responseMapper.toItemResponse(item, inventories.get(bookId), editorialPrices.get(bookId));
        });
    }

    @Transactional(readOnly = true)
    public Page<InventoryCountResultResponse> findResults(
            Long sessionId,
            InventoryCountDifferenceType difference,
            String query,
            Pageable pageable
    ) {
        accessService.require(sessionId);
        String searchQuery = normalizeSearchQuery(query);
        String identifierQuery = normalizeIdentifierQuery(query);

        Page<com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult> page;

        if (searchQuery == null) {
            page = difference == null
                    ? resultRepository.findAllBySessionIdAndCountedQuantityIsNotNull(
                    sessionId,
                    pageable
            )
                    : resultRepository.findAllBySessionIdAndCountedQuantityIsNotNullAndDifferenceType(
                    sessionId,
                    difference,
                    pageable
            );
        } else {
            boolean searchIdentifier = identifierQuery != null;

            page = resultRepository.search(
                    sessionId,
                    difference,
                    searchQuery,
                    searchIdentifier,
                    searchIdentifier ? identifierQuery : "",
                    pageable
            );
        }

        return page.map(responseMapper::toResultResponse);
    }

    @Transactional
    public InventoryCountSessionResponse review(Long sessionId) {
        InventoryCountSession session = accessService.requireForUpdate(sessionId);
        reviewService.review(session);
        return responseMapper.toSessionResponse(session);
    }

    @Transactional
    public InventoryCountSessionResponse updateConfiguration(
            Long sessionId,
            UpdateInventoryCountConfigurationRequest request
    ) {
        InventoryCountSession session = accessService.requireForUpdate(sessionId);
        configurationService.update(session, request);
        return responseMapper.toSessionResponse(session);
    }

    @Transactional
    public InventoryCountSessionResponse apply(Long sessionId, boolean allowConcurrentMovements) {
        InventoryCountSession session = accessService.requireForUpdate(sessionId);
        applyService.apply(session, allowConcurrentMovements);
        return responseMapper.toSessionResponse(session);
    }

    @Transactional
    public InventoryCountSessionResponse revert(Long sessionId) {
        InventoryCountSession session = accessService.requireForUpdate(sessionId);
        revertService.revert(session);
        return responseMapper.toSessionResponse(session);
    }

    @Transactional
    public void cancel(Long sessionId) {
        InventoryCountSession session = accessService.requireForUpdate(sessionId);
        if (session.getStatus() != InventoryCountStatus.OPEN && session.getStatus() != InventoryCountStatus.REVIEW) {
            throw new BusinessException("Solo se puede cancelar un conteo abierto o en revisión");
        }

        session.setStatus(InventoryCountStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public InventoryCountReportSummaryResponse report(Long sessionId) {
        return reportService.build(accessService.require(sessionId));
    }

    private void validateModePurpose(InventoryCountMode mode, InventoryCountPurpose purpose) {
        if (purpose == InventoryCountPurpose.DELIVERY && mode != InventoryCountMode.ADDITIVE) {
            throw new BusinessException("Una recepción de mercadería debe utilizar modo ADDITIVE");
        }
        if ((purpose == InventoryCountPurpose.AUDIT || purpose == InventoryCountPurpose.RECONCILIATION)
                && mode != InventoryCountMode.ABSOLUTE) {
            throw new BusinessException("Una auditoría o reconciliación debe utilizar modo ABSOLUTE");
        }
    }


    private String normalizeSearchQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIdentifierQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.replace("-", "").replace(" ", "").trim();
        return normalized.matches("[0-9Xx]+") ? normalized : null;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
