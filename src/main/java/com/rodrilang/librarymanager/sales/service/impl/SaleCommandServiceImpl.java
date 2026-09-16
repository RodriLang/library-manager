package com.rodrilang.librarymanager.sales.service.impl;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.repository.FiscalDocumentRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeSyncType;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeSyncRequestedEvent;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.requirement.dto.internal.AddPurchaseRequirementCommand;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;
import com.rodrilang.librarymanager.purchasing.requirement.service.PurchaseRequirementService;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.sales.dto.internal.SaleTotals;
import com.rodrilang.librarymanager.sales.dto.request.CancelSaleRequest;
import com.rodrilang.librarymanager.sales.dto.request.CreateSaleItemRequest;
import com.rodrilang.librarymanager.sales.dto.request.CreateSalePaymentRequest;
import com.rodrilang.librarymanager.sales.dto.request.CreateSaleRequest;
import com.rodrilang.librarymanager.sales.dto.response.SaleDetailResponse;
import com.rodrilang.librarymanager.sales.mapper.SaleMapper;
import com.rodrilang.librarymanager.sales.model.Sale;
import com.rodrilang.librarymanager.sales.model.SaleItem;
import com.rodrilang.librarymanager.sales.model.SaleOrigin;
import com.rodrilang.librarymanager.sales.model.SalePayment;
import com.rodrilang.librarymanager.sales.model.SaleStatus;
import com.rodrilang.librarymanager.sales.repository.SaleItemRepository;
import com.rodrilang.librarymanager.sales.repository.SalePaymentRepository;
import com.rodrilang.librarymanager.sales.repository.SaleRepository;
import com.rodrilang.librarymanager.sales.service.SaleCalculator;
import com.rodrilang.librarymanager.sales.service.SaleCommandService;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleCommandServiceImpl implements SaleCommandService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository itemRepository;
    private final SalePaymentRepository paymentRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;

    private final InventoryStockService inventoryStockService;
    private final PurchaseRequirementService purchaseRequirementService;
    private final SaleCalculator calculator;
    private final SaleMapper mapper;

    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public SaleDetailResponse create(CreateSaleRequest request) {
        validateRequest(request);

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Long userId = bookstoreContext.getCurrentUserId();

        List<CreateSaleItemRequest> requestedItems = new ArrayList<>(request.items());
        validateNoDuplicateInventories(requestedItems);

        List<Long> inventoryIds = requestedItems.stream()
                .map(CreateSaleItemRequest::inventoryId)
                .sorted()
                .toList();

        List<Inventory> lockedInventories = inventoryRepository
                .findAllByBookstoreIdAndIdsForUpdate(bookstoreId, inventoryIds);

        if (lockedInventories.size() != inventoryIds.size()) {
            throw new BusinessException(
                    "Uno o más libros no existen en el inventario de la librería actual."
            );
        }

        Map<Long, Inventory> inventoryById = lockedInventories.stream()
                .collect(Collectors.toMap(
                        Inventory::getId,
                        inventory -> inventory,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, BigDecimal> lineSubtotalByInventoryId = new LinkedHashMap<>();

        for (CreateSaleItemRequest item : requestedItems) {
            Inventory inventory = inventoryById.get(item.inventoryId());
            validateInventoryForSale(inventory, item.quantity());

            lineSubtotalByInventoryId.put(
                    inventory.getId(),
                    calculator.calculateLineSubtotal(
                            inventory.getSalePrice(),
                            item.quantity()
                    )
            );
        }

        SaleTotals totals = calculator.calculateTotals(
                lineSubtotalByInventoryId.values(),
                request.discountAmount()
        );
        calculator.validatePayments(totals.total(), request.payments());

        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);
        User user = userRepository.getReferenceById(userId);

        Sale sale = saleRepository.saveAndFlush(
                Sale.builder()
                        .bookstore(bookstore)
                        .createdBy(user)
                        .status(SaleStatus.COMPLETED)
                        .origin(SaleOrigin.MANUAL)
                        .soldAt(Instant.now())
                        .subtotal(totals.subtotal())
                        .discountAmount(totals.discountAmount())
                        .total(totals.total())
                        .notes(normalizeNullableText(request.notes()))
                        .build()
        );

        List<SaleItem> saleItems = new ArrayList<>(requestedItems.size());
        for (CreateSaleItemRequest item : requestedItems) {
            Inventory inventory = inventoryById.get(item.inventoryId());

            saleItems.add(
                    SaleItem.builder()
                            .sale(sale)
                            .inventory(inventory)
                            .quantity(item.quantity())
                            .unitPrice(calculator.money(inventory.getSalePrice()))
                            .subtotal(lineSubtotalByInventoryId.get(inventory.getId()))
                            .description(inventory.getBook().getTitle())
                            .isbn(inventory.getBook().getPreferredIsbn())
                            .build()
            );
        }
        saleItems = itemRepository.saveAllAndFlush(saleItems);

        List<SalePayment> payments = request.payments().stream()
                .map(payment -> toPayment(sale, payment))
                .toList();
        payments = paymentRepository.saveAllAndFlush(payments);

        Map<Long, CreateSaleItemRequest> requestByInventoryId = requestedItems.stream()
                .collect(Collectors.toMap(
                        CreateSaleItemRequest::inventoryId,
                        item -> item
                ));

        List<SaleItem> stockOrderedItems = saleItems.stream()
                .sorted(Comparator.comparing(item -> item.getInventory().getId()))
                .toList();

        for (SaleItem saleItem : stockOrderedItems) {
            inventoryStockService.changeStock(
                    saleItem.getInventory().getId(),
                    new InventoryStockChangeCommand(
                            -saleItem.getQuantity(),
                            InventoryMovementType.SALE,
                            InventoryMovementSource.MANUAL,
                            InventoryMovementReferenceType.SALE,
                            sale.getId().toString(),
                            "Venta #" + sale.getId()
                    )
            );

            CreateSaleItemRequest itemRequest = requestByInventoryId.get(
                    saleItem.getInventory().getId()
            );

            if (Boolean.TRUE.equals(itemRequest.replenish())) {
                purchaseRequirementService.addRequirement(
                        new AddPurchaseRequirementCommand(
                                saleItem.getInventory().getBook().getId(),
                                saleItem.getQuantity(),
                                PurchaseRequirementSourceType.SALE_ITEM,
                                saleItem.getId().toString(),
                                null
                        )
                );
            }

            publishStockSync(saleItem.getInventory().getId());
        }

        return mapper.toDetailResponse(sale, saleItems, payments);
    }

    @Override
    @Transactional
    public SaleDetailResponse cancel(Long saleId, CancelSaleRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Long userId = bookstoreContext.getCurrentUserId();

        Sale sale = saleRepository.findByIdAndBookstoreIdForUpdate(saleId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la venta con ID: " + saleId
                ));

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessException("La venta ya se encuentra cancelada.");
        }

        if (fiscalDocumentRepository.existsBySaleIdAndStatusIn(
                sale.getId(),
                Set.of(
                        FiscalDocumentStatus.AUTHORIZED,
                        FiscalDocumentStatus.AUTHORIZING,
                        FiscalDocumentStatus.RECONCILIATION_REQUIRED
                )
        )) {
            throw new BusinessException(
                    "La venta posee un comprobante fiscal emitido o pendiente. "
                            + "Para cancelarla deberá emitirse la nota de crédito correspondiente."
            );
        }

        List<SaleItem> items = itemRepository.findAllBySaleIdOrderByIdAsc(sale.getId());
        if (items.isEmpty()) {
            throw new BusinessException("La venta no tiene ítems para revertir.");
        }

        for (SaleItem item : items.stream()
                .sorted(Comparator.comparing(saleItem -> saleItem.getInventory().getId()))
                .toList()) {

            inventoryStockService.changeStock(
                    item.getInventory().getId(),
                    new InventoryStockChangeCommand(
                            item.getQuantity(),
                            InventoryMovementType.RETURN,
                            InventoryMovementSource.MANUAL,
                            InventoryMovementReferenceType.SALE,
                            sale.getId().toString(),
                            "Cancelación de venta #" + sale.getId()
                    )
            );

            purchaseRequirementService.undoAutomaticSource(
                    PurchaseRequirementSourceType.SALE_ITEM,
                    item.getId().toString()
            );

            publishStockSync(item.getInventory().getId());
        }

        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledAt(Instant.now());
        sale.setCancelledBy(userRepository.getReferenceById(userId));
        sale.setCancellationReason(
                request != null ? normalizeNullableText(request.reason()) : null
        );

        saleRepository.flush();

        return mapper.toDetailResponse(
                sale,
                items,
                paymentRepository.findAllBySaleIdOrderByIdAsc(sale.getId())
        );
    }

    private void validateRequest(CreateSaleRequest request) {
        if (request == null) {
            throw new BusinessException("Debe especificarse la venta.");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("La venta debe contener al menos un libro.");
        }
        if (request.payments() == null) {
            throw new BusinessException("Deben informarse los pagos de la venta.");
        }
    }

    private void validateNoDuplicateInventories(List<CreateSaleItemRequest> items) {
        Set<Long> inventoryIds = new HashSet<>();

        for (CreateSaleItemRequest item : items) {
            if (item == null || item.inventoryId() == null) {
                throw new BusinessException("Todos los ítems deben indicar un inventario.");
            }
            if (!inventoryIds.add(item.inventoryId())) {
                throw new BusinessException(
                        "Un mismo libro no puede repetirse en la venta. Debe unificarse su cantidad."
                );
            }
        }
    }

    private void validateInventoryForSale(Inventory inventory, Integer quantity) {
        if (!Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException(
                    "El libro \"" + inventory.getBook().getTitle() + "\" se encuentra inactivo."
            );
        }
        if (!Boolean.TRUE.equals(inventory.getBook().getActive())) {
            throw new BusinessException(
                    "El libro \"" + inventory.getBook().getTitle() + "\" se encuentra inactivo en el catálogo."
            );
        }
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("La cantidad vendida debe ser mayor a cero.");
        }
        if (inventory.getStock() < quantity) {
            throw new BusinessException(
                    "No hay stock suficiente de \"" + inventory.getBook().getTitle()
                            + "\". Disponible: " + inventory.getStock() + "."
            );
        }
    }

    private SalePayment toPayment(
            Sale sale,
            CreateSalePaymentRequest request
    ) {
        if (request.method() == null) {
            throw new BusinessException("Debe especificarse el medio de pago.");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new BusinessException("El importe del pago debe ser mayor a cero.");
        }

        return SalePayment.builder()
                .sale(sale)
                .method(request.method())
                .amount(calculator.money(request.amount()))
                .reference(normalizeNullableText(request.reference()))
                .build();
    }

    private void publishStockSync(Long inventoryId) {
        eventPublisher.publishEvent(
                new TiendanubeSyncRequestedEvent(
                        inventoryId,
                        TiendanubeSyncType.STOCK
                )
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
