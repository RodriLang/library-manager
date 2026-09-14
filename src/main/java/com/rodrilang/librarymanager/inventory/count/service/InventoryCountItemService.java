package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.count.dto.internal.InventoryCountItemUpsertCommand;
import com.rodrilang.librarymanager.inventory.count.dto.internal.InventoryCountResolution;
import com.rodrilang.librarymanager.inventory.count.dto.request.ScanInventoryCountRequest;
import com.rodrilang.librarymanager.inventory.count.dto.request.UpdateInventoryCountItemRequest;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountItemResponse;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemScanRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountResultRepository;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InventoryCountItemService {

    private final InventoryCountSessionAccessService accessService;
    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountItemScanRepository scanRepository;
    private final InventoryCountResultRepository resultRepository;
    private final InventoryCountBookResolver bookResolver;
    private final InventoryCountPriceResolver priceResolver;
    private final InventoryCountReviewResetService reviewResetService;
    private final InventoryCountPendingApplyService pendingApplyService;
    private final InventoryCountResponseMapper responseMapper;
    private final IsbnService isbnService;

    @Transactional
    public InventoryCountItemResponse scan(Long sessionId, ScanInventoryCountRequest request) {
        InventoryCountSession session = accessService.requireForUpdate(sessionId);
        requireScannable(session);
        reviewResetService.resetToOpen(session);

        ParsedIsbn isbn = isbnService.parse(request.code());
        String normalized = isbn.valid() ? isbn.isbn13() : isbnService.normalize(request.code());
        if (normalized == null) {
            throw new BusinessException("El código escaneado no contiene un identificador válido");
        }

        InventoryCountResolution resolution = isbn.valid()
                ? bookResolver.resolve(isbn, session.getBookstore(), session.getCondition())
                : new InventoryCountResolution(null, null, InventoryCountItemStatus.INVALID_IDENTIFIER);

        Instant now = Instant.now();
        Long itemId = scanRepository.upsertScan(new InventoryCountItemUpsertCommand(
                session.getId(),
                request.code().trim(),
                normalized,
                isbn.isbn10(),
                isbn.isbn13(),
                resolution.book() != null ? resolution.book().getId() : null,
                resolution.catalogCandidate() != null ? resolution.catalogCandidate().getId() : null,
                resolution.status(),
                now
        ));

        InventoryCountItem item = itemRepository.findByIdAndSessionId(itemId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el ítem recién escaneado"));

        return responseMapper.toItemResponse(item);
    }

    @Transactional
    public InventoryCountItemResponse update(Long sessionId, Long itemId, UpdateInventoryCountItemRequest request) {
        InventoryCountSession session = accessService.requireForUpdate(sessionId);
        InventoryCountItem item = requireItem(itemId, sessionId);
        requireEditable(session, item);

        if (request.quantity() == null && request.salePrice() == null) {
            throw new BusinessException("Debe indicar una cantidad o un precio para modificar");
        }

        if (session.getStatus() == InventoryCountStatus.REVIEW) {
            reviewResetService.resetToOpen(session);
        }

        if (request.quantity() != null) {
            item.setQuantity(request.quantity());
        }
        if (request.salePrice() != null) {
            item.setSalePriceOverride(request.salePrice());
        }

        refreshKnownItemStatus(item);
        InventoryCountItem saved = itemRepository.save(item);

        if (session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING) {
            pendingApplyService.applyIfReady(saved);
        }

        return responseMapper.toItemResponse(saved);
    }

    @Transactional
    public void delete(Long sessionId, Long itemId) {
        InventoryCountSession session = accessService.requireForUpdate(sessionId);
        InventoryCountItem item = requireItem(itemId, sessionId);
        requireEditable(session, item);

        if (session.getStatus() == InventoryCountStatus.REVIEW) {
            reviewResetService.resetToOpen(session);
        }

        if (session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING && item.getBook() != null) {
            resultRepository.deleteBySessionIdAndBookIdAndBaselineFalseAndAppliedAtIsNull(sessionId, item.getBook().getId());
        }

        itemRepository.delete(item);
        pendingApplyService.refreshSessionStatus(session);
    }

    private void refreshKnownItemStatus(InventoryCountItem item) {
        if (item.getBook() == null) {
            return;
        }

        boolean hasPrice = item.getSalePriceOverride() != null || !priceResolver.requiresPrice(
                item.getBook(),
                item.getSession().getBookstore().getId(),
                item.getSession().getCondition()
        );

        item.setStatus(hasPrice ? InventoryCountItemStatus.RESOLVED : InventoryCountItemStatus.PENDING_PRICE);
    }

    private InventoryCountItem requireItem(Long itemId, Long sessionId) {
        return itemRepository.findByIdAndSessionId(itemId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el ítem del conteo"));
    }

    private void requireScannable(InventoryCountSession session) {
        if (session.getStatus() != InventoryCountStatus.OPEN && session.getStatus() != InventoryCountStatus.REVIEW) {
            throw new BusinessException("No se pueden agregar escaneos en el estado actual del conteo");
        }
    }

    private void requireEditable(InventoryCountSession session, InventoryCountItem item) {
        if (session.getStatus() == InventoryCountStatus.OPEN || session.getStatus() == InventoryCountStatus.REVIEW) {
            return;
        }
        if (session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING && item.getAppliedAt() == null) {
            return;
        }

        throw new BusinessException("El ítem ya no puede modificarse en el estado actual del conteo");
    }
}
