package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.pricing.dto.ApplyInventoryPriceImportRequest;
import com.rodrilang.librarymanager.inventory.pricing.dto.InventoryPriceImportApplyStartResponse;
import com.rodrilang.librarymanager.inventory.pricing.dto.InventoryPriceImportProcessingStatusResponse;
import com.rodrilang.librarymanager.inventory.pricing.event.InventoryPriceImportApplyRequestedEvent;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportItem;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportClassification;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportItemRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportProviderRowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryPriceImportApplyService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    private final BookstoreContext bookstoreContext;
    private final InventoryPriceImportRepository importRepository;
    private final InventoryPriceImportItemRepository itemRepository;
    private final InventoryPriceImportProviderRowRepository providerRowRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InventoryPriceImportApplyStartResponse start(
            Long importId,
            ApplyInventoryPriceImportRequest request
    ) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        InventoryPriceImport priceImport = getImportForUpdate(importId, bookstoreId);

        if (priceImport.getStatus() != InventoryPriceImportStatus.PREVIEW_READY) {
            throw new BusinessException(statusMessage(priceImport.getStatus()));
        }

        List<InventoryPriceImportItem> items =
                itemRepository.findAllByPriceImportIdOrderByRowNumberAsc(importId);

        Set<Long> requestedIds = requestedIds(items, request);

        // A correctly matched UNCHANGED row is still valuable: its presence in
        // the list confirms that the current amount remains valid. This must not
        // depend on whether the frontend includes unchanged rows among the
        // explicitly selected price changes.
        items.stream()
                .filter(item -> !item.isDiscarded())
                .filter(item -> item.getInventory() != null)
                .filter(item -> item.getIncomingPrice() != null && item.getIncomingPrice().signum() > 0)
                .filter(item -> item.getClassification() == InventoryPriceImportClassification.UNCHANGED)
                .map(InventoryPriceImportItem::getId)
                .forEach(requestedIds::add);

        for (InventoryPriceImportItem item : items) {
            item.setSelectedForApply(requestedIds.contains(item.getId()));
            item.setApplied(false);
        }

        Instant startedAt = Instant.now();
        priceImport.setStatus(InventoryPriceImportStatus.PROCESSING);
        priceImport.setProcessingStartedAt(startedAt);
        priceImport.setProcessingFinishedAt(null);
        priceImport.setProcessingError(null);
        priceImport.setAppliedAt(null);
        priceImport.setAppliedRows(0);
        priceImport.setSkippedRows(0);

        itemRepository.saveAll(items);

        eventPublisher.publishEvent(
                new InventoryPriceImportApplyRequestedEvent(
                        importId,
                        bookstoreContext.getCurrentUserId()
                )
        );

        return new InventoryPriceImportApplyStartResponse(
                importId,
                InventoryPriceImportStatus.PROCESSING,
                startedAt
        );
    }

    @Transactional(readOnly = true)
    public InventoryPriceImportProcessingStatusResponse status(Long importId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        InventoryPriceImport priceImport = getImport(importId, bookstoreId);

        return new InventoryPriceImportProcessingStatusResponse(
                priceImport.getId(),
                priceImport.getStatus(),
                priceImport.getProcessingStartedAt(),
                priceImport.getProcessingFinishedAt(),
                priceImport.getAppliedRows(),
                priceImport.getSkippedRows(),
                priceImport.getProcessingError()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long importId, Throwable exception) {
        InventoryPriceImport priceImport = importRepository.findById(importId)
                .orElse(null);

        if (priceImport == null || priceImport.getStatus() != InventoryPriceImportStatus.PROCESSING) {
            return;
        }

        priceImport.setStatus(InventoryPriceImportStatus.FAILED);
        priceImport.setProcessingFinishedAt(Instant.now());
        priceImport.setProcessingError(errorMessage(exception));
        providerRowRepository.deleteAllByPriceImportId(importId);
    }

    private Set<Long> requestedIds(
            List<InventoryPriceImportItem> items,
            ApplyInventoryPriceImportRequest request
    ) {
        if (request != null && request.itemIds() != null) {
            return new HashSet<>(request.itemIds());
        }

        return items.stream()
                .filter(InventoryPriceImportItem::isSelectedDefault)
                .map(InventoryPriceImportItem::getId)
                .collect(Collectors.toSet());
    }

    private InventoryPriceImport getImport(Long importId, Long bookstoreId) {
        return importRepository.findByIdAndBookstoreId(importId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la importación solicitada."));
    }

    private InventoryPriceImport getImportForUpdate(Long importId, Long bookstoreId) {
        return importRepository.findByIdAndBookstoreIdForUpdate(importId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la importación solicitada."));
    }

    private String statusMessage(InventoryPriceImportStatus status) {
        return switch (status) {
            case PROCESSING -> "La importación ya se está procesando.";
            case APPLIED -> "La importación ya fue aplicada.";
            case CANCELLED -> "La importación fue cancelada y no puede aplicarse.";
            case FAILED -> "La importación falló y no puede volver a aplicarse desde este estado.";
            case PREVIEW_READY -> "La importación no se puede iniciar en su estado actual.";
        };
    }

    private String errorMessage(Throwable exception) {
        String message = exception instanceof BusinessException
                ? exception.getMessage()
                : "No se pudo completar la actualización de precios.";
        if (message == null || message.isBlank()) {
            message = "No se pudo completar la actualización de precios.";
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
