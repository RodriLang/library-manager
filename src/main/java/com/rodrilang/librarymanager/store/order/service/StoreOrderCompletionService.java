package com.rodrilang.librarymanager.store.order.service;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeSyncType;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeSyncRequestedEvent;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.sales.model.Sale;
import com.rodrilang.librarymanager.sales.model.SaleItem;
import com.rodrilang.librarymanager.sales.model.SaleOrigin;
import com.rodrilang.librarymanager.sales.model.SalePayment;
import com.rodrilang.librarymanager.sales.model.SaleStatus;
import com.rodrilang.librarymanager.sales.repository.SaleItemRepository;
import com.rodrilang.librarymanager.sales.repository.SalePaymentRepository;
import com.rodrilang.librarymanager.sales.repository.SaleRepository;
import com.rodrilang.librarymanager.store.order.dto.CompleteStoreOrderRequest;
import com.rodrilang.librarymanager.store.order.model.*;
import com.rodrilang.librarymanager.store.order.repository.StoreStockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreOrderCompletionService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryStockService inventoryStockService;
    private final StoreStockReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final BookstoreContext bookstoreContext;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Sale complete(StoreOrder order, List<StoreOrderItem> orderItems, CompleteStoreOrderRequest request) {
        if (order.getStatus() == StoreOrderStatus.COMPLETED) {
            if (order.getSale() == null) {
                throw new BusinessException("El pedido figura completado pero no tiene una venta asociada.");
            }
            return order.getSale();
        }
        if (order.getStatus() != StoreOrderStatus.CONFIRMED) {
            throw new BusinessException("El pedido debe estar confirmado antes de entregarlo.");
        }
        if (order.getFulfillmentStatus() != StoreFulfillmentStatus.READY_FOR_PICKUP) {
            throw new BusinessException("El pedido debe estar listo para retirar antes de marcarlo como entregado.");
        }
        if (order.getPaymentMethod() == StorePaymentMethod.MERCADO_PAGO) {
            if (order.getPaymentStatus() != StorePaymentStatus.PAID) {
                throw new BusinessException("El pago de Mercado Pago todavía no está acreditado.");
            }
        } else if (request == null || request.paymentMethod() == null) {
            throw new BusinessException("Indicá el medio de pago utilizado al entregar el pedido.");
        }
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException("El pedido no contiene productos.");
        }

        List<StoreStockReservation> reservations = reservationRepository.findAllByOrderIdOrderByIdAsc(order.getId());
        if (reservations.size() != orderItems.size()
                || reservations.stream().anyMatch(r -> r.getStatus() != StoreReservationStatus.ACTIVE)) {
            throw new BusinessException("La reserva de stock del pedido ya no está disponible.");
        }

        List<Long> inventoryIds = orderItems.stream()
                .map(item -> item.getInventory().getId())
                .sorted()
                .toList();
        List<Inventory> lockedInventories = inventoryRepository
                .findAllByBookstoreIdAndIdsForUpdate(order.getBookstore().getId(), inventoryIds);
        if (lockedInventories.size() != inventoryIds.size()) {
            throw new BusinessException("Uno o más productos del pedido ya no existen en el inventario.");
        }
        Map<Long, Inventory> inventoryById = lockedInventories.stream()
                .collect(Collectors.toMap(Inventory::getId, Function.identity()));

        for (StoreOrderItem item : orderItems) {
            Inventory inventory = inventoryById.get(item.getInventory().getId());
            int stock = inventory.getStock() == null ? 0 : inventory.getStock();
            if (stock < item.getQuantity()) {
                throw new BusinessException("No hay stock físico suficiente de \"" + item.getTitle()
                        + "\" para completar el pedido. Stock actual: " + stock + ".");
            }
        }

        Instant now = Instant.now();
        User currentUser = userRepository.getReferenceById(bookstoreContext.getCurrentUserId());

        Sale sale = saleRepository.saveAndFlush(Sale.builder()
                .bookstore(order.getBookstore())
                .createdBy(currentUser)
                .status(SaleStatus.COMPLETED)
                .origin(SaleOrigin.ANAQUEL_STORE)
                .soldAt(now)
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .total(order.getTotal())
                .externalReference(order.getOrderNumber())
                .notes(saleNotes(order))
                .build());

        List<SaleItem> saleItems = new ArrayList<>(orderItems.size());
        for (StoreOrderItem orderItem : orderItems) {
            Inventory inventory = inventoryById.get(orderItem.getInventory().getId());
            saleItems.add(SaleItem.builder()
                    .sale(sale)
                    .inventory(inventory)
                    .quantity(orderItem.getQuantity())
                    .unitPrice(orderItem.getUnitPrice())
                    .subtotal(orderItem.getSubtotal())
                    .description(orderItem.getTitle())
                    .isbn(orderItem.getIsbn())
                    .build());
        }
        saleItems = saleItemRepository.saveAllAndFlush(saleItems);

        salePaymentRepository.saveAndFlush(SalePayment.builder()
                .sale(sale)
                .method(order.getPaymentMethod() == StorePaymentMethod.MERCADO_PAGO
                        ? com.rodrilang.librarymanager.payment.model.PaymentMethod.DIGITAL_WALLET
                        : request.paymentMethod())
                .amount(order.getTotal())
                .reference(order.getPaymentMethod() == StorePaymentMethod.MERCADO_PAGO
                        ? order.getPaymentExternalId()
                        : normalize(request.paymentReference()))
                .build());

        List<SaleItem> stockOrderedItems = saleItems.stream()
                .sorted(Comparator.comparing(item -> item.getInventory().getId()))
                .toList();
        for (SaleItem saleItem : stockOrderedItems) {
            var stockResult = inventoryStockService.changeStock(
                    saleItem.getInventory().getId(),
                    new InventoryStockChangeCommand(
                            -saleItem.getQuantity(),
                            InventoryMovementType.SALE,
                            InventoryMovementSource.ANAQUEL_STORE,
                            InventoryMovementReferenceType.SALE,
                            sale.getId().toString(),
                            "Venta Anaquel Store · pedido " + order.getOrderNumber()
                    )
            );

            int consignmentSold = Math.max(0, -stockResult.movement().getConsignmentDelta());
            if (consignmentSold > 0) {
                saleItem.setConsignmentQuantity(consignmentSold);
                saleItem.setConsignmentProvider(stockResult.movement().getConsignmentProvider());
                saleItemRepository.save(saleItem);
            }

            eventPublisher.publishEvent(new TiendanubeSyncRequestedEvent(
                    saleItem.getInventory().getId(),
                    TiendanubeSyncType.STOCK
            ));
        }

        for (StoreStockReservation reservation : reservations) {
            reservation.setStatus(StoreReservationStatus.CONSUMED);
            reservation.setReleasedAt(now);
            reservation.setExpiresAt(null);
        }
        reservationRepository.saveAll(reservations);

        order.setSale(sale);
        order.setStatus(StoreOrderStatus.COMPLETED);
        order.setPaymentStatus(StorePaymentStatus.PAID);
        order.setFulfillmentStatus(StoreFulfillmentStatus.DELIVERED);
        order.setCompletedAt(now);
        order.setReservationExpiresAt(null);

        return sale;
    }

    private String saleNotes(StoreOrder order) {
        String base = "Pedido Anaquel Store " + order.getOrderNumber();
        if (order.getNotes() == null || order.getNotes().isBlank()) return base;
        String result = base + " · " + order.getNotes().trim();
        return result.length() <= 1000 ? result : result.substring(0, 1000);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
