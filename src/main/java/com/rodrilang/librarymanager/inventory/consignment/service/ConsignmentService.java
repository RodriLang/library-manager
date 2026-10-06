package com.rodrilang.librarymanager.inventory.consignment.service;

import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.consignment.dto.*;
import com.rodrilang.librarymanager.inventory.consignment.model.*;
import com.rodrilang.librarymanager.inventory.consignment.repository.*;
import com.rodrilang.librarymanager.inventory.movement.repository.InventoryMovementRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.model.InventoryMovement;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsignmentService {
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ConsignmentSettlementRepository settlementRepository;
    private final ConsignmentSettlementItemRepository settlementItemRepository;
    private final ProviderRepository providerRepository;
    private final BookstoreService bookstoreService;
    private final UserRepository userRepository;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public Page<ConsignmentInventoryResponse> inventory(Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return inventoryRepository.findAllByBookstoreIdAndConsignmentStockGreaterThan(bookstoreId, 0, pageable)
                .map(this::toInventoryResponse);
    }

    @Transactional(readOnly = true)
    public Page<ConsignmentSaleResponse> sales(Long providerId, Boolean settled, Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Page<InventoryMovement> page = movementRepository.findConsignmentSales(bookstoreId, providerId, settled, pageable);
        Set<Long> ids = page.getContent().stream().map(InventoryMovement::getId).collect(Collectors.toSet());
        Map<Long, ConsignmentSettlementItem> settlementByMovement = settlementItemRepository.findAllByInventoryMovementIdIn(ids).stream()
                .filter(i -> ids.contains(i.getInventoryMovement().getId()))
                .collect(Collectors.toMap(i -> i.getInventoryMovement().getId(), Function.identity()));

        return page.map(m -> toSaleResponse(m, settlementByMovement.get(m.getId())));
    }

    @Transactional
    public ConsignmentSettlementResponse settle(CreateConsignmentSettlementRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Provider provider = providerRepository.findById(request.providerId())
                .filter(Provider::isPurchasable)
                .orElseThrow(() -> new BusinessException("El proveedor no existe o no está activo."));

        List<Long> ids = request.movementIds().stream().distinct().toList();
        List<InventoryMovement> movements = movementRepository.findConsignmentSalesByIds(bookstoreId, ids);
        if (movements.size() != ids.size()) {
            throw new BusinessException("Una o más ventas consignadas no pertenecen a la librería actual.");
        }
        if (movements.stream().anyMatch(m -> m.getConsignmentProvider() == null || !m.getConsignmentProvider().getId().equals(provider.getId()))) {
            throw new BusinessException("Todas las ventas de una rendición deben pertenecer al mismo proveedor.");
        }
        if (movements.stream().anyMatch(m -> settlementItemRepository.existsByInventoryMovementId(m.getId()))) {
            throw new BusinessException("Una o más ventas ya fueron incluidas en una rendición.");
        }

        ConsignmentSettlement settlement = settlementRepository.saveAndFlush(ConsignmentSettlement.builder()
                .bookstore(bookstoreService.getEntityById(bookstoreId))
                .provider(provider)
                .settlementNumber("TMP-" + UUID.randomUUID())
                .status(ConsignmentSettlementStatus.SETTLED)
                .settledAt(Instant.now())
                .notes(clean(request.notes()))
                .createdByUser(userRepository.getReferenceById(bookstoreContext.getCurrentUserId()))
                .build());
        settlement.setSettlementNumber("CON-%d-%06d".formatted(Year.now().getValue(), settlement.getId()));

        List<ConsignmentSettlementItem> items = movements.stream()
                .map(m -> ConsignmentSettlementItem.builder()
                        .settlement(settlement)
                        .inventoryMovement(m)
                        .quantity(Math.max(0, -value(m.getConsignmentDelta())))
                        .build())
                .toList();
        settlementItemRepository.saveAll(items);
        return toSettlementResponse(settlement, items);
    }

    @Transactional
    public ConsignmentSettlementResponse cancelSettlement(Long settlementId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        ConsignmentSettlement settlement = settlementRepository.findByIdAndBookstoreId(settlementId, bookstoreId)
                .orElseThrow(() -> new BusinessException("No se encontró la rendición."));
        if (settlement.getStatus() == ConsignmentSettlementStatus.CANCELLED) {
            throw new BusinessException("La rendición ya se encuentra cancelada.");
        }
        List<ConsignmentSettlementItem> items = settlementItemRepository.findAllBySettlementIdOrderByIdAsc(settlementId);
        settlementItemRepository.deleteAll(items);
        settlement.setStatus(ConsignmentSettlementStatus.CANCELLED);
        return toSettlementResponse(settlement, List.of());
    }

    @Transactional(readOnly = true)
    public Page<ConsignmentSettlementResponse> settlements(Pageable pageable) {
        return settlementRepository.findAllByBookstoreId(bookstoreContext.getCurrentBookstoreId(), pageable)
                .map(s -> toSettlementResponse(s, settlementItemRepository.findAllBySettlementIdOrderByIdAsc(s.getId())));
    }

    private ConsignmentInventoryResponse toInventoryResponse(Inventory i) {
        return new ConsignmentInventoryResponse(
                i.getId(), i.getBook().getId(), i.getBook().getPreferredIsbn(), i.getBook().getTitle(), i.getBook().getCoverUrl(),
                i.getCondition(), i.getStock(), i.getConsignmentStock(), i.getStock() - i.getConsignmentStock(),
                i.getConsignmentProvider() != null ? i.getConsignmentProvider().getId() : null,
                i.getConsignmentProvider() != null ? i.getConsignmentProvider().getName() : null
        );
    }

    private ConsignmentSaleResponse toSaleResponse(InventoryMovement m, ConsignmentSettlementItem settlementItem) {
        ConsignmentSettlement s = settlementItem != null ? settlementItem.getSettlement() : null;
        return new ConsignmentSaleResponse(
                m.getId(), m.getInventory().getId(), m.getInventory().getBook().getId(), m.getInventory().getBook().getPreferredIsbn(),
                m.getInventory().getBook().getTitle(), m.getInventory().getBook().getCoverUrl(), Math.max(0, -value(m.getConsignmentDelta())),
                m.getConsignmentProvider() != null ? m.getConsignmentProvider().getId() : null,
                m.getConsignmentProvider() != null ? m.getConsignmentProvider().getName() : null,
                m.getSource(), m.getReferenceId(), m.getCreatedAt(), s != null,
                s != null ? s.getId() : null, s != null ? s.getSettlementNumber() : null
        );
    }

    private ConsignmentSettlementResponse toSettlementResponse(ConsignmentSettlement s, List<ConsignmentSettlementItem> items) {
        return new ConsignmentSettlementResponse(
                s.getId(), s.getSettlementNumber(), s.getProvider().getId(), s.getProvider().getName(), s.getStatus(), s.getSettledAt(), s.getNotes(),
                items.size(), items.stream().mapToInt(i -> value(i.getQuantity())).sum()
        );
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
