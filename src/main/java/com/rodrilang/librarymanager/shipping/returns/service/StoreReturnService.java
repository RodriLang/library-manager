package com.rodrilang.librarymanager.shipping.returns.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.shipping.returns.dto.*;
import com.rodrilang.librarymanager.shipping.operations.ReturnShippingClientRegistry;
import com.rodrilang.librarymanager.shipping.operations.ReturnShippingLabelClientRegistry;
import com.rodrilang.librarymanager.shipping.shipment.provider.ShipmentLabelResult;
import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.shipping.returns.model.*;
import com.rodrilang.librarymanager.shipping.returns.repository.*;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;
import com.rodrilang.librarymanager.shipping.shipment.repository.StoreShipmentRepository;
import com.rodrilang.librarymanager.shipping.shipment.repository.StoreShipmentItemRepository;
import com.rodrilang.librarymanager.store.order.model.StoreOrder;
import com.rodrilang.librarymanager.store.order.model.StoreOrderItem;
import com.rodrilang.librarymanager.store.order.model.StoreOrderStatus;
import com.rodrilang.librarymanager.store.order.repository.StoreOrderItemRepository;
import com.rodrilang.librarymanager.store.order.repository.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StoreReturnService {
    private final BookstoreContext bookstoreContext;
    private final StoreOrderRepository orderRepository;
    private final StoreOrderItemRepository orderItemRepository;
    private final StoreShipmentRepository shipmentRepository;
    private final StoreShipmentItemRepository shipmentItemRepository;
    private final StoreReturnRepository returnRepository;
    private final StoreReturnItemRepository returnItemRepository;
    private final InventoryStockService inventoryStockService;
    private final ReturnShippingClientRegistry returnShippingClientRegistry;
    private final ReturnShippingLabelClientRegistry returnShippingLabelClientRegistry;

    @Transactional(readOnly = true)
    public List<StoreReturnResponse> list(Long orderId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        requireOrder(orderId, bookstoreId);
        return returnRepository.findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(orderId, bookstoreId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public StoreReturnResponse create(Long orderId, CreateStoreReturnRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        StoreOrder order = requireOrder(orderId, bookstoreId);
        if (order.getStatus() == StoreOrderStatus.PENDING || order.getStatus() == StoreOrderStatus.CANCELLED || order.getStatus() == StoreOrderStatus.EXPIRED) {
            throw new BusinessException("El pedido todavía no admite devoluciones.");
        }
        List<StoreOrderItem> orderItems = orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId);
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("Seleccioná al menos un libro para la devolución.");
        }
        Map<Long, StoreOrderItem> byInventory = new HashMap<>();
        for (StoreOrderItem item : orderItems) byInventory.put(item.getInventory().getId(), item);

        StoreShipment shipment = null;
        Map<Long, Integer> shipmentQuantities = Map.of();
        if (request.shipmentId() != null) {
            shipment = shipmentRepository.findByIdAndOrderIdAndBookstoreId(request.shipmentId(), orderId, bookstoreId)
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró el envío asociado."));
            if (shipment.getStatus() == ShipmentStatus.DRAFT || shipment.getStatus() == ShipmentStatus.READY_TO_SHIP || shipment.getStatus() == ShipmentStatus.CANCELLED) {
                throw new BusinessException("El bulto seleccionado todavía no admite devoluciones.");
            }
            Map<Long, Integer> quantities = new HashMap<>();
            shipmentItemRepository.findAllByShipmentIdOrderByIdAsc(shipment.getId())
                    .forEach(item -> quantities.put(item.getOrderItem().getId(), item.getQuantity()));
            shipmentQuantities = quantities;
        }

        StoreReturn storeReturn = returnRepository.save(StoreReturn.builder()
                .bookstore(order.getBookstore()).order(order).shipment(shipment)
                .status(StoreReturnStatus.REQUESTED)
                .reason(normalize(request.reason())).notes(normalize(request.notes()))
                .provider(shipment != null ? shipment.getProvider() : null)
                .requestedAt(Instant.now()).build());

        Set<Long> seen = new HashSet<>();
        for (StoreReturnItemRequest requested : request.items()) {
            if (requested == null || requested.inventoryId() == null || requested.quantity() == null || requested.quantity() <= 0) {
                throw new BusinessException("Las cantidades de devolución deben ser mayores a cero.");
            }
            if (!seen.add(requested.inventoryId())) throw new BusinessException("Un libro no puede repetirse en la misma devolución.");
            StoreOrderItem orderItem = byInventory.get(requested.inventoryId());
            if (orderItem == null) throw new BusinessException("Uno de los libros no pertenece al pedido.");
            int availableQuantity;
            if (shipment != null) {
                int shippedInPackage = shipmentQuantities.getOrDefault(orderItem.getId(), 0);
                if (shippedInPackage == 0) throw new BusinessException("Uno de los libros no pertenece al bulto seleccionado.");
                int alreadyReturned = Optional.ofNullable(returnItemRepository.sumActiveReturnedForShipment(orderItem.getId(), shipment.getId())).orElse(0L).intValue();
                availableQuantity = shippedInPackage - alreadyReturned;
            } else {
                int alreadyReturned = Optional.ofNullable(returnItemRepository.sumActiveReturned(orderItem.getId())).orElse(0L).intValue();
                availableQuantity = orderItem.getQuantity() - alreadyReturned;
            }
            if (requested.quantity() > availableQuantity) throw new BusinessException("La cantidad supera las unidades disponibles para devolución.");
            returnItemRepository.save(StoreReturnItem.builder().storeReturn(storeReturn).orderItem(orderItem).quantity(requested.quantity()).build());
        }
        return toResponse(storeReturn);
    }

    @Transactional public StoreReturnResponse approve(Long orderId, Long returnId) {
        StoreReturn r = requireReturn(orderId, returnId); ensureStatus(r, StoreReturnStatus.REQUESTED);
        r.setStatus(StoreReturnStatus.APPROVED); r.setApprovedAt(Instant.now()); return toResponse(r);
    }
    @Transactional public StoreReturnResponse markInTransit(Long orderId, Long returnId) {
        StoreReturn r = requireReturn(orderId, returnId);
        if (r.getStatus() != StoreReturnStatus.APPROVED && r.getStatus() != StoreReturnStatus.REQUESTED) throw new BusinessException("La devolución no puede marcarse en tránsito desde su estado actual.");
        r.setStatus(StoreReturnStatus.IN_TRANSIT); if (r.getApprovedAt() == null) r.setApprovedAt(Instant.now()); return toResponse(r);
    }
    @Transactional public StoreReturnResponse receive(Long orderId, Long returnId) {
        StoreReturn r = requireReturn(orderId, returnId);
        if (r.getStatus() == StoreReturnStatus.CANCELLED || r.getStatus() == StoreReturnStatus.REJECTED || r.getStatus() == StoreReturnStatus.COMPLETED) throw new BusinessException("La devolución no puede recibirse desde su estado actual.");
        r.setStatus(StoreReturnStatus.RECEIVED); r.setReceivedAt(Instant.now()); return toResponse(r);
    }
    @Transactional public StoreReturnResponse complete(Long orderId, Long returnId, CompleteStoreReturnRequest request) {
        StoreReturn r = requireReturn(orderId, returnId);
        ensureStatus(r, StoreReturnStatus.RECEIVED);
        List<StoreReturnItem> returnItems = returnItemRepository.findAllByStoreReturnIdOrderByIdAsc(r.getId());
        Map<Long, StoreReturnItem> byInventory = new HashMap<>();
        returnItems.forEach(item -> byInventory.put(item.getOrderItem().getInventory().getId(), item));
        Set<Long> seen = new HashSet<>();
        List<ReturnRestockItemRequest> restockItems = request == null || request.restockItems() == null ? List.of() : request.restockItems();
        for (ReturnRestockItemRequest restock : restockItems) {
            if (restock == null || restock.inventoryId() == null || restock.quantity() == null || restock.quantity() <= 0) {
                throw new BusinessException("Las cantidades a reingresar deben ser mayores a cero.");
            }
            if (!seen.add(restock.inventoryId())) throw new BusinessException("Un libro no puede repetirse en el reingreso de stock.");
            StoreReturnItem item = byInventory.get(restock.inventoryId());
            if (item == null) throw new BusinessException("Uno de los libros no pertenece a la devolución.");
            int already = item.getRestockedQuantity() == null ? 0 : item.getRestockedQuantity();
            int available = item.getQuantity() - already;
            if (restock.quantity() > available) throw new BusinessException("La cantidad a reingresar supera las unidades recibidas en la devolución.");
            inventoryStockService.changeStock(restock.inventoryId(), new InventoryStockChangeCommand(
                    restock.quantity(), InventoryMovementType.RETURN, InventoryMovementSource.ANAQUEL_STORE,
                    InventoryMovementReferenceType.STORE_RETURN, String.valueOf(r.getId()),
                    "Reingreso por devolución del pedido " + r.getOrder().getOrderNumber()));
            item.setRestockedQuantity(already + restock.quantity());
        }
        r.setStatus(StoreReturnStatus.COMPLETED);
        r.setCompletedAt(Instant.now());
        return toResponse(r);
    }
    @Transactional public StoreReturnResponse cancel(Long orderId, Long returnId) {
        StoreReturn r = requireReturn(orderId, returnId);
        if (r.getStatus() == StoreReturnStatus.COMPLETED) throw new BusinessException("Una devolución completada no puede cancelarse.");
        r.setStatus(StoreReturnStatus.CANCELLED); r.setCancelledAt(Instant.now()); return toResponse(r);
    }
    @Transactional public StoreReturnResponse reject(Long orderId, Long returnId) {
        StoreReturn r = requireReturn(orderId, returnId); ensureStatus(r, StoreReturnStatus.REQUESTED);
        r.setStatus(StoreReturnStatus.REJECTED); return toResponse(r);
    }
    @Transactional public StoreReturnResponse updateShipping(Long orderId, Long returnId, UpdateStoreReturnShippingRequest request) {
        StoreReturn r = requireReturn(orderId, returnId);
        r.setProviderReturnId(normalize(request.providerReturnId())); r.setTrackingNumber(normalize(request.trackingNumber())); r.setTrackingUrl(normalize(request.trackingUrl()));
        return toResponse(r);
    }

    @Transactional
    public StoreReturnResponse createProviderReturn(Long orderId, Long returnId) {
        StoreReturn r = requireReturn(orderId, returnId);
        ensureStatus(r, StoreReturnStatus.APPROVED);
        if (r.getProvider() == null) throw new BusinessException("La devolución no tiene un transportista asociado.");
        if (r.getTrackingNumber() != null && !r.getTrackingNumber().isBlank()) throw new BusinessException("La devolución ya tiene un envío generado en el transportista.");
        var client = returnShippingClientRegistry.find(r.getProvider())
                .orElseThrow(() -> new BusinessException("La logística inversa automática para " + r.getProvider() + " todavía no está disponible."));
        var result = client.createReturn(r);
        r.setProviderReturnId(normalize(result.externalId()));
        r.setTrackingNumber(normalize(result.trackingNumber()));
        r.setTrackingUrl(normalize(result.trackingUrl()));
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public ShipmentLabelResult returnLabel(Long orderId, Long returnId) {
        StoreReturn r = requireReturn(orderId, returnId);
        if (r.getProvider() == null) throw new BusinessException("La devolución no tiene un transportista asociado.");
        var client = returnShippingLabelClientRegistry.find(r.getProvider())
                .orElseThrow(() -> new BusinessException("El rótulo automático de devolución para " + r.getProvider() + " todavía no está disponible."));
        return client.label(r);
    }

    private StoreOrder requireOrder(Long orderId, Long bookstoreId) { return orderRepository.findByIdAndBookstoreId(orderId, bookstoreId).orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido.")); }
    private StoreReturn requireReturn(Long orderId, Long returnId) { return returnRepository.findByIdAndOrderIdAndBookstoreId(returnId, orderId, bookstoreContext.getCurrentBookstoreId()).orElseThrow(() -> new ResourceNotFoundException("No se encontró la devolución.")); }
    private void ensureStatus(StoreReturn r, StoreReturnStatus expected) { if (r.getStatus() != expected) throw new BusinessException("La devolución no está en el estado requerido para esta acción."); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private StoreReturnResponse toResponse(StoreReturn r) {
        List<StoreReturnItemResponse> items = returnItemRepository.findAllByStoreReturnIdOrderByIdAsc(r.getId()).stream()
                .map(i -> new StoreReturnItemResponse(i.getOrderItem().getInventory().getId(), i.getOrderItem().getTitle(), i.getOrderItem().getIsbn(), i.getQuantity(), i.getRestockedQuantity())).toList();
        return new StoreReturnResponse(r.getId(), r.getShipment() == null ? null : r.getShipment().getId(), r.getStatus(), r.getReason(), r.getNotes(), r.getProvider(), r.getProviderReturnId(), r.getTrackingNumber(), r.getTrackingUrl(), r.getRequestedAt(), r.getApprovedAt(), r.getReceivedAt(), r.getCompletedAt(), r.getCancelledAt(), items);
    }
}
