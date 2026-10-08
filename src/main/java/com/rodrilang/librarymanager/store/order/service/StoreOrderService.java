package com.rodrilang.librarymanager.store.order.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.catalog.contribution.service.BookstoreBookFieldOverrideService;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceService;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.store.channel.SalesChannelType;
import com.rodrilang.librarymanager.store.model.BookstoreStore;
import com.rodrilang.librarymanager.store.model.StorePublication;
import com.rodrilang.librarymanager.store.order.dto.*;
import com.rodrilang.librarymanager.store.order.event.StoreOrderNotificationType;
import com.rodrilang.librarymanager.store.order.notification.StoreOrderNotificationPublisher;
import com.rodrilang.librarymanager.store.order.model.*;
import com.rodrilang.librarymanager.store.order.repository.*;
import com.rodrilang.librarymanager.store.repository.BookstoreStoreRepository;
import com.rodrilang.librarymanager.store.repository.StorePublicationRepository;
import com.rodrilang.librarymanager.store.service.SalesChannelService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreOrderService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final BookstoreStoreRepository storeRepository;
    private final StorePublicationRepository publicationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryPriceService priceService;
    private final SalesChannelService salesChannelService;
    private final StoreOrderRepository orderRepository;
    private final StoreOrderItemRepository itemRepository;
    private final StoreStockReservationRepository reservationRepository;
    private final BookstoreContext bookstoreContext;
    private final BookstoreBookFieldOverrideService overrideService;
    private final StoreOrderCompletionService completionService;
    private final StoreOrderNotificationPublisher notificationPublisher;

    @Value("${app.store.order-reservation-minutes:1440}")
    private long reservationMinutes;

    @Transactional
    public StoreOrderPublicResponse create(UUID publicStoreId, CreateStoreOrderRequest request) {
        BookstoreStore store = requireActiveStore(publicStoreId);

        StoreOrder existing = orderRepository.findByStoreIdAndClientRequestId(store.getId(), request.clientRequestId()).orElse(null);
        if (existing != null) return toPublic(existing, loadItems(existing.getId()));

        validateCreateRequest(request);
        Map<Long, Integer> requested = aggregateItems(request.items());
        List<Long> inventoryIds = requested.keySet().stream().sorted().toList();

        List<Inventory> inventories = inventoryRepository.findAllByBookstoreIdAndIdsForUpdate(store.getBookstore().getId(), inventoryIds);
        if (inventories.size() != inventoryIds.size()) {
            throw new BusinessException("Uno o más productos ya no están disponibles en esta librería.");
        }
        Map<Long, Inventory> inventoryById = inventories.stream().collect(Collectors.toMap(Inventory::getId, Function.identity()));

        Map<Long, StorePublication> publicationByInventory = publicationRepository
                .findAllByStoreIdAndInventoryIdIn(store.getId(), inventoryIds).stream()
                .collect(Collectors.toMap(p -> p.getInventory().getId(), Function.identity()));
        Map<Long, InventoryPrice> priceByInventory = priceService.currentFor(inventoryIds);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(Math.max(1, reservationMinutes)));
        List<PendingLine> lines = new ArrayList<>();
        BigDecimal subtotal = ZERO;

        for (Long inventoryId : inventoryIds) {
            Inventory inventory = inventoryById.get(inventoryId);
            StorePublication publication = publicationByInventory.get(inventoryId);
            if (publication == null || !Boolean.TRUE.equals(publication.getPublished())
                    || !Boolean.TRUE.equals(inventory.getActive()) || !Boolean.TRUE.equals(inventory.getBook().getActive())) {
                throw new BusinessException("Uno de los productos ya no está publicado en la tienda.");
            }
            InventoryPrice price = priceByInventory.get(inventoryId);
            if (price == null || price.getAmount() == null) {
                throw new BusinessException("Uno de los productos ya no tiene un precio vigente.");
            }

            int quantity = requested.get(inventoryId);
            int physicalStock = Math.max(0, value(inventory.getStock()));
            int reserved = Math.toIntExact(Optional.ofNullable(reservationRepository.sumActiveReserved(
                    inventoryId, StoreReservationStatus.ACTIVE, now)).orElse(0L));
            int available = Math.max(0, physicalStock - reserved);
            if (quantity > available) {
                throw new BusinessException("No hay stock suficiente para \"" + inventory.getBook().getTitle()
                        + "\". Disponible: " + available + ".");
            }

            BigDecimal unitPrice = money(price.getAmount());
            BigDecimal lineSubtotal = money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
            String effectiveTitle = overrideService.resolve(inventory.getBook(), store.getBookstore().getId()).title();
            String title = hasText(publication.getCustomTitle()) ? publication.getCustomTitle().trim() : effectiveTitle;
            lines.add(new PendingLine(inventory, quantity, unitPrice, lineSubtotal, title, inventory.getBook().getPreferredIsbn()));
            subtotal = subtotal.add(lineSubtotal);
        }

        subtotal = money(subtotal);
        StoreOrder order = orderRepository.saveAndFlush(StoreOrder.builder()
                .publicId(UUID.randomUUID())
                .store(store)
                .bookstore(store.getBookstore())
                .clientRequestId(request.clientRequestId())
                .orderNumber(nextOrderNumber())
                .trackingToken(UUID.randomUUID())
                .status(StoreOrderStatus.PENDING)
                .paymentStatus(StorePaymentStatus.PENDING)
                .fulfillmentStatus(StoreFulfillmentStatus.PENDING)
                .paymentMethod(request.paymentMethod())
                .deliveryMethod(request.deliveryMethod())
                .customerName(request.customerName().trim())
                .customerEmail(request.customerEmail().trim().toLowerCase(Locale.ROOT))
                .customerPhone(normalize(request.customerPhone()))
                .notes(normalize(request.notes()))
                .subtotal(subtotal)
                .discountAmount(ZERO)
                .shippingCost(ZERO)
                .total(subtotal)
                .reservationExpiresAt(expiresAt)
                .build());

        List<StoreOrderItem> items = new ArrayList<>();
        for (PendingLine line : lines) {
            items.add(itemRepository.save(StoreOrderItem.builder()
                    .order(order)
                    .inventory(line.inventory())
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .subtotal(line.subtotal())
                    .title(line.title())
                    .isbn(line.isbn())
                    .build()));
        }
        itemRepository.flush();

        List<StoreStockReservation> reservations = items.stream().map(item -> StoreStockReservation.builder()
                .order(order)
                .orderItem(item)
                .inventory(item.getInventory())
                .quantity(item.getQuantity())
                .status(StoreReservationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build()).toList();
        reservationRepository.saveAll(reservations);
        notificationPublisher.publish(StoreOrderNotificationType.RECEIVED, order, items);

        return toPublic(order, items);
    }

    @Transactional(readOnly = true)
    public StoreOrderPublicResponse track(UUID publicStoreId, String orderNumber, UUID token) {
        if (storeRepository.findByPublicId(publicStoreId).isEmpty()) {
            throw new ResourceNotFoundException("No se encontró la tienda.");
        }
        StoreOrder order = orderRepository.findByStorePublicIdAndOrderNumberAndTrackingToken(publicStoreId, orderNumber, token)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido."));
        return toPublic(order, loadItems(order.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<StoreOrderSummaryResponse> adminList(String q, StoreOrderStatus status,
                                                              StorePaymentStatus paymentStatus,
                                                              StoreFulfillmentStatus fulfillmentStatus,
                                                              Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Pageable safePageable = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"))
                : pageable;
        Page<StoreOrder> page = orderRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("bookstore").get("id"), bookstoreId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (paymentStatus != null) predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            if (fulfillmentStatus != null) predicates.add(cb.equal(root.get("fulfillmentStatus"), fulfillmentStatus));
            if (hasText(q)) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), like),
                        cb.like(cb.lower(root.get("customerName")), like),
                        cb.like(cb.lower(root.get("customerEmail")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        }, safePageable);
        return PageResponse.of(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public StoreOrderResponse adminDetail(Long orderId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        StoreOrder order = orderRepository.findByIdAndBookstoreId(orderId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido."));
        return toAdmin(order, loadItems(orderId));
    }

    @Transactional
    public StoreOrderResponse confirm(Long orderId) {
        StoreOrder order = requireAdminOrderForUpdate(orderId);
        if (order.getStatus() == StoreOrderStatus.CONFIRMED) return toAdmin(order, loadItems(orderId));
        if (order.getStatus() != StoreOrderStatus.PENDING) {
            throw new BusinessException("Sólo se pueden confirmar pedidos pendientes.");
        }
        Instant now = Instant.now();
        List<StoreStockReservation> reservations = reservationRepository.findAllByOrderIdOrderByIdAsc(orderId);
        if (reservations.isEmpty() || reservations.stream().anyMatch(r -> r.getStatus() != StoreReservationStatus.ACTIVE
                || (r.getExpiresAt() != null && !r.getExpiresAt().isAfter(now)))) {
            throw new BusinessException("La reserva del pedido venció. El cliente deberá realizar un nuevo pedido.");
        }
        reservations.forEach(r -> r.setExpiresAt(null));
        order.setStatus(StoreOrderStatus.CONFIRMED);
        order.setConfirmedAt(now);
        order.setReservationExpiresAt(null);
        reservationRepository.saveAll(reservations);
        List<StoreOrderItem> items = loadItems(orderId);
        notificationPublisher.publish(StoreOrderNotificationType.CONFIRMED, order, items);
        return toAdmin(order, items);
    }

    @Transactional
    public StoreOrderResponse cancel(Long orderId, CancelStoreOrderRequest request) {
        StoreOrder order = requireAdminOrderForUpdate(orderId);
        if (order.getStatus() == StoreOrderStatus.CANCELLED) return toAdmin(order, loadItems(orderId));
        if (order.getStatus() == StoreOrderStatus.EXPIRED || order.getStatus() == StoreOrderStatus.COMPLETED) {
            throw new BusinessException("El pedido ya no puede cancelarse.");
        }
        Instant now = Instant.now();
        releaseReservations(orderId, StoreReservationStatus.RELEASED, now);
        order.setStatus(StoreOrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setCancellationReason(request == null ? null : normalize(request.reason()));
        order.setReservationExpiresAt(null);
        List<StoreOrderItem> items = loadItems(orderId);
        notificationPublisher.publish(StoreOrderNotificationType.CANCELLED, order, items);
        return toAdmin(order, items);
    }

    @Transactional
    public StoreOrderResponse updateFulfillment(Long orderId, UpdateStoreFulfillmentRequest request) {
        StoreOrder order = requireAdminOrderForUpdate(orderId);
        if (order.getStatus() != StoreOrderStatus.CONFIRMED) {
            throw new BusinessException("Confirmá el pedido antes de comenzar a prepararlo.");
        }
        StoreFulfillmentStatus current = order.getFulfillmentStatus();
        StoreFulfillmentStatus target = request.status();
        boolean allowed = (current == StoreFulfillmentStatus.PENDING && target == StoreFulfillmentStatus.PREPARING)
                || (current == StoreFulfillmentStatus.PREPARING && target == StoreFulfillmentStatus.READY_FOR_PICKUP)
                || current == target;
        if (!allowed) {
            throw new BusinessException("Cambio de estado de preparación no permitido en esta etapa.");
        }
        order.setFulfillmentStatus(target);
        List<StoreOrderItem> items = loadItems(orderId);
        if (current != target && target == StoreFulfillmentStatus.READY_FOR_PICKUP) {
            notificationPublisher.publish(StoreOrderNotificationType.READY_FOR_PICKUP, order, items);
        }
        return toAdmin(order, items);
    }


    @Transactional
    public StoreOrderResponse complete(Long orderId, CompleteStoreOrderRequest request) {
        StoreOrder order = requireAdminOrderForUpdate(orderId);
        if (order.getStatus() == StoreOrderStatus.COMPLETED) {
            return toAdmin(order, loadItems(orderId));
        }
        List<StoreOrderItem> items = loadItems(orderId);
        completionService.complete(order, items, request);
        notificationPublisher.publish(StoreOrderNotificationType.COMPLETED, order, items);
        return toAdmin(order, items);
    }

    @Transactional
    public void expirePendingOrders() {
        Instant now = Instant.now();
        for (Long orderId : orderRepository.findExpiredPendingIds(StoreOrderStatus.PENDING, now)) {
            StoreOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order == null || order.getStatus() != StoreOrderStatus.PENDING) continue;
            expireOrder(order, reservationRepository.findAllByOrderIdOrderByIdAsc(orderId), now);
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> activeReservedByInventoryIds(Collection<Long> inventoryIds) {
        if (inventoryIds == null || inventoryIds.isEmpty()) return Map.of();
        Map<Long, Integer> result = new HashMap<>();
        for (ReservedQuantityProjection row : reservationRepository.sumActiveByInventoryIds(inventoryIds, StoreReservationStatus.ACTIVE, Instant.now())) {
            result.put(row.getInventoryId(), Math.toIntExact(Optional.ofNullable(row.getQuantity()).orElse(0L)));
        }
        return result;
    }

    private StoreOrder requireAdminOrderForUpdate(Long orderId) {
        return orderRepository.findByIdAndBookstoreIdForUpdate(orderId, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido."));
    }

    private BookstoreStore requireActiveStore(UUID publicStoreId) {
        BookstoreStore store = storeRepository.findByPublicId(publicStoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la tienda."));
        if (!Boolean.TRUE.equals(store.getBookstore().getActive())
                || !salesChannelService.isEnabled(store.getBookstore().getId(), SalesChannelType.ANAQUEL_STORE)) {
            throw new ResourceNotFoundException("La tienda no está disponible.");
        }
        return store;
    }

    private void validateCreateRequest(CreateStoreOrderRequest request) {
        if (request.deliveryMethod() != StoreDeliveryMethod.PICKUP) {
            throw new BusinessException("Por ahora la tienda admite únicamente retiro en la librería.");
        }
        if (request.paymentMethod() != StorePaymentMethod.PAY_AT_STORE) {
            throw new BusinessException("Por ahora el pago se realiza en la librería.");
        }
    }

    private Map<Long, Integer> aggregateItems(List<CreateStoreOrderItemRequest> items) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (CreateStoreOrderItemRequest item : items) {
            int next = result.getOrDefault(item.inventoryId(), 0) + item.quantity();
            if (next > 999) throw new BusinessException("La cantidad solicitada de un producto es demasiado alta.");
            result.put(item.inventoryId(), next);
        }
        return result;
    }

    private String nextOrderNumber() {
        for (int i = 0; i < 10; i++) {
            String value = "AQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
            if (!orderRepository.existsByOrderNumber(value)) return value;
        }
        return "AQ-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private void expireOrder(StoreOrder order, List<StoreStockReservation> reservations, Instant now) {
        for (StoreStockReservation reservation : reservations) {
            if (reservation.getStatus() == StoreReservationStatus.ACTIVE) {
                reservation.setStatus(StoreReservationStatus.EXPIRED);
                reservation.setReleasedAt(now);
            }
        }
        reservationRepository.saveAll(reservations);
        order.setStatus(StoreOrderStatus.EXPIRED);
        order.setReservationExpiresAt(null);
    }

    private void releaseReservations(Long orderId, StoreReservationStatus status, Instant now) {
        List<StoreStockReservation> reservations = reservationRepository.findAllByOrderIdOrderByIdAsc(orderId);
        for (StoreStockReservation reservation : reservations) {
            if (reservation.getStatus() == StoreReservationStatus.ACTIVE) {
                reservation.setStatus(status);
                reservation.setReleasedAt(now);
            }
        }
        reservationRepository.saveAll(reservations);
    }

    private List<StoreOrderItem> loadItems(Long orderId) { return itemRepository.findAllByOrderIdOrderByIdAsc(orderId); }
    private List<StoreOrderItemResponse> itemResponses(List<StoreOrderItem> items) {
        return items.stream().map(i -> new StoreOrderItemResponse(i.getInventory().getId(), i.getTitle(), i.getIsbn(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal())).toList();
    }
    private StoreOrderPublicResponse toPublic(StoreOrder o, List<StoreOrderItem> items) {
        return new StoreOrderPublicResponse(o.getPublicId(), o.getOrderNumber(), o.getTrackingToken(), o.getStatus(), o.getPaymentStatus(), o.getFulfillmentStatus(),
                o.getPaymentMethod(), o.getDeliveryMethod(), o.getCustomerName(), o.getCustomerEmail(), o.getSubtotal(), o.getShippingCost(), o.getTotal(),
                o.getReservationExpiresAt(), o.getCreatedAt(), itemResponses(items));
    }
    private StoreOrderResponse toAdmin(StoreOrder o, List<StoreOrderItem> items) {
        return new StoreOrderResponse(o.getId(), o.getPublicId(), o.getOrderNumber(), o.getTrackingToken(), o.getStatus(), o.getPaymentStatus(), o.getFulfillmentStatus(),
                o.getPaymentMethod(), o.getDeliveryMethod(), o.getCustomerName(), o.getCustomerEmail(), o.getCustomerPhone(), o.getNotes(), o.getSubtotal(),
                o.getDiscountAmount(), o.getShippingCost(), o.getTotal(), o.getReservationExpiresAt(), o.getCreatedAt(), o.getConfirmedAt(), o.getCancelledAt(),
                o.getCancellationReason(), o.getSale() != null ? o.getSale().getId() : null, o.getCompletedAt(), itemResponses(items));
    }
    private StoreOrderSummaryResponse toSummary(StoreOrder o) {
        return new StoreOrderSummaryResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPaymentStatus(), o.getFulfillmentStatus(), o.getCustomerName(),
                o.getCustomerEmail(), o.getTotal(), o.getReservationExpiresAt(), o.getCreatedAt());
    }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private int value(Integer value) { return value == null ? 0 : value; }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String normalize(String value) { return hasText(value) ? value.trim() : null; }

    private record PendingLine(Inventory inventory, int quantity, BigDecimal unitPrice, BigDecimal subtotal, String title, String isbn) {}
}
