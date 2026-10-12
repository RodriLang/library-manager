package com.rodrilang.librarymanager.shipping.shipment.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.shipment.dto.*;
import com.rodrilang.librarymanager.shipping.shipment.model.*;
import com.rodrilang.librarymanager.shipping.shipment.repository.*;
import com.rodrilang.librarymanager.shipping.shipment.provider.*;
import com.rodrilang.librarymanager.store.order.model.*;
import com.rodrilang.librarymanager.store.order.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StoreShipmentService {
    private final BookstoreContext bookstoreContext;
    private final StoreOrderRepository orderRepository;
    private final StoreOrderItemRepository orderItemRepository;
    private final StoreShipmentRepository shipmentRepository;
    private final StoreShipmentItemRepository shipmentItemRepository;
    private final ShipmentTrackingService trackingService;
    private final ShipmentCreationClientRegistry creationClientRegistry;
    private final ShipmentCancellationClientRegistry cancellationClientRegistry;
    private final ShipmentLabelClientRegistry labelClientRegistry;

    @Transactional(readOnly = true)
    public List<StoreShipmentResponse> list(Long orderId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        requireOrder(orderId, bookstoreId);
        return shipmentRepository.findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(orderId, bookstoreId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public StoreShipmentResponse createDraft(Long orderId, CreateStoreShipmentRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        StoreOrder order = requireOrder(orderId, bookstoreId);
        if (order.getDeliveryMethod() != StoreDeliveryMethod.SHIPPING) throw new BusinessException("Este pedido es para retiro en librería y no requiere un envío.");
        if (order.getStatus() != StoreOrderStatus.CONFIRMED) throw new BusinessException("Confirmá el pedido antes de preparar el envío.");

        List<StoreOrderItem> orderItems = orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId);
        Map<Long, StoreOrderItem> byInventory = new LinkedHashMap<>();
        for (StoreOrderItem item : orderItems) byInventory.put(item.getInventory().getId(), item);

        List<ShipmentItemAllocationRequest> requested = request == null ? null : request.items();
        Map<StoreOrderItem, Integer> allocation = new LinkedHashMap<>();
        if (request != null && (requested == null || requested.isEmpty())) throw new BusinessException("Seleccioná al menos una unidad para incluir en el bulto.");
        if (requested == null) {
            for (StoreOrderItem item : orderItems) {
                int used = Optional.ofNullable(shipmentItemRepository.sumAllocatedQuantity(item.getId())).orElse(0L).intValue();
                int remaining = item.getQuantity() - used;
                if (remaining > 0) allocation.put(item, remaining);
            }
        } else {
            Set<Long> seen = new HashSet<>();
            for (ShipmentItemAllocationRequest row : requested) {
                if (row == null || row.inventoryId() == null || row.quantity() == null || row.quantity() <= 0) throw new BusinessException("Las cantidades del envío deben ser mayores a cero.");
                if (!seen.add(row.inventoryId())) throw new BusinessException("Un libro no puede repetirse dentro del mismo bulto.");
                StoreOrderItem item = byInventory.get(row.inventoryId());
                if (item == null) throw new BusinessException("Uno de los libros no pertenece al pedido.");
                int used = Optional.ofNullable(shipmentItemRepository.sumAllocatedQuantity(item.getId())).orElse(0L).intValue();
                int remaining = item.getQuantity() - used;
                if (row.quantity() > remaining) throw new BusinessException("La cantidad supera las unidades pendientes de asignar a envíos.");
                allocation.put(item, row.quantity());
            }
        }
        if (allocation.isEmpty()) throw new BusinessException("Todos los artículos del pedido ya están asignados a envíos activos.");

        List<StoreShipment> existing = shipmentRepository.findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(orderId, bookstoreId);
        int sequence = existing.stream().map(StoreShipment::getPackageSequence).filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        ShipmentPackageEstimate packageEstimate = estimatePackage(allocation);
        BigDecimal declared = allocation.entrySet().stream()
                .map(e -> e.getKey().getUnitPrice().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StoreShipment shipment = shipmentRepository.save(StoreShipment.builder()
                .bookstore(order.getBookstore()).order(order)
                .provider(provider(order.getShippingProvider())).serviceCode(order.getShippingServiceCode()).serviceName(order.getShippingServiceName())
                .status(ShipmentStatus.DRAFT).packageSequence(sequence).notes(normalize(request == null ? null : request.notes()))
                .weightGrams(packageEstimate.weightGrams()).widthCm(packageEstimate.widthCm())
                .heightCm(packageEstimate.heightCm()).depthCm(packageEstimate.depthCm())
                .declaredValue(declared).build());
        for (var entry : allocation.entrySet()) shipmentItemRepository.save(StoreShipmentItem.builder().shipment(shipment).orderItem(entry.getKey()).quantity(entry.getValue()).build());
        trackingService.recordLocalEvent(shipment, ShipmentStatus.DRAFT, "Bulto " + sequence + " preparado en Anaquel.");
        return toResponse(shipment);
    }

    @Transactional
    public StoreShipmentResponse updateMetadata(Long orderId, Long shipmentId, UpdateShipmentMetadataRequest request) {
        StoreShipment shipment = requireShipment(orderId, shipmentId);
        shipment.setProviderShipmentId(normalize(request.providerShipmentId())); shipment.setTrackingNumber(normalize(request.trackingNumber()));
        shipment.setTrackingUrl(normalize(request.trackingUrl())); shipment.setLabelUrl(normalize(request.labelUrl()));
        shipment.setProviderStatus(normalize(request.providerStatus())); shipment.setProviderStatusDetail(normalize(request.providerStatusDetail()));
        if (shipment.getProviderShipmentId() != null && shipment.getCreatedAtProvider() == null) shipment.setCreatedAtProvider(Instant.now());
        if (shipment.getStatus() == ShipmentStatus.DRAFT && shipment.getTrackingNumber() != null) trackingService.recordLocalEvent(shipment, ShipmentStatus.READY_TO_SHIP, "Seguimiento asignado. Envío listo para despachar.");
        return toResponse(shipment);
    }

    @Transactional public StoreShipmentResponse refreshTracking(Long orderId, Long shipmentId) { StoreShipment s=requireShipment(orderId,shipmentId); if(s.getStatus()!=ShipmentStatus.CANCELLED&&s.getStatus()!=ShipmentStatus.DELIVERED) trackingService.refresh(s); return toResponse(s); }
    @Transactional public StoreShipmentResponse addTrackingEvent(Long orderId, Long shipmentId, CreateShipmentTrackingEventRequest request) { StoreShipment s=requireShipment(orderId,shipmentId); trackingService.addManualEvent(s,request); return toResponse(s); }
    @Transactional
    public StoreShipmentResponse createProviderShipment(Long orderId, Long shipmentId) {
        StoreShipment s = requireShipment(orderId, shipmentId);
        if (s.getStatus() == ShipmentStatus.CANCELLED || s.getStatus() == ShipmentStatus.DELIVERED) throw new BusinessException("El envío ya está finalizado.");
        if (s.getTrackingNumber() != null && !s.getTrackingNumber().isBlank()) throw new BusinessException("El bulto ya tiene un envío generado en el transportista.");
        ShipmentCreationClient client = creationClientRegistry.find(s.getProvider())
                .orElseThrow(() -> new BusinessException("La generación automática de envíos para " + s.getProvider() + " todavía no está disponible."));
        ShipmentCreationResult result = client.create(s);
        s.setProviderShipmentId(normalize(result.providerShipmentId()));
        s.setTrackingNumber(normalize(result.trackingNumber()));
        s.setTrackingUrl(normalize(result.trackingUrl()));
        s.setProviderStatus(normalize(result.providerStatus()));
        s.setProviderStatusDetail(normalize(result.providerStatusDetail()));
        s.setCreatedAtProvider(Instant.now());
        trackingService.recordLocalEvent(s, ShipmentStatus.READY_TO_SHIP, "Envío generado en " + s.getProvider() + ".");
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public ShipmentLabelResult label(Long orderId, Long shipmentId) {
        StoreShipment s = requireShipment(orderId, shipmentId);
        ShipmentLabelClient client = labelClientRegistry.find(s.getProvider())
                .orElseThrow(() -> new BusinessException("La generación de rótulos para " + s.getProvider() + " todavía no está disponible."));
        return client.label(s);
    }

    @Transactional public StoreShipmentResponse markDispatched(Long orderId, Long shipmentId) { StoreShipment s=requireShipment(orderId,shipmentId); if(s.getStatus()!=ShipmentStatus.DRAFT&&s.getStatus()!=ShipmentStatus.READY_TO_SHIP) throw new BusinessException("El envío no está en un estado que permita despacharlo."); trackingService.recordDispatched(s); return toResponse(s); }
    @Transactional public StoreShipmentResponse markDelivered(Long orderId, Long shipmentId) { StoreShipment s=requireShipment(orderId,shipmentId); if(s.getStatus()==ShipmentStatus.CANCELLED) throw new BusinessException("Un envío cancelado no puede marcarse como entregado."); trackingService.recordLocalEvent(s,ShipmentStatus.DELIVERED,"Pedido entregado."); return toResponse(s); }
    @Transactional public StoreShipmentResponse cancel(Long orderId, Long shipmentId) {
        StoreShipment s=requireShipment(orderId,shipmentId);
        if(s.getStatus()==ShipmentStatus.DELIVERED) throw new BusinessException("Un envío entregado no puede cancelarse.");
        if (s.getTrackingNumber() != null && !s.getTrackingNumber().isBlank()) {
            cancellationClientRegistry.find(s.getProvider()).ifPresent(client -> client.cancel(s));
        }
        trackingService.recordLocalEvent(s,ShipmentStatus.CANCELLED,"Envío cancelado.");
        return toResponse(s);
    }

    private StoreOrder requireOrder(Long orderId, Long bookstoreId) { return orderRepository.findByIdAndBookstoreId(orderId, bookstoreId).orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido.")); }
    private StoreShipment requireShipment(Long orderId, Long shipmentId) { return shipmentRepository.findByIdAndOrderIdAndBookstoreId(shipmentId, orderId, bookstoreContext.getCurrentBookstoreId()).orElseThrow(() -> new ResourceNotFoundException("No se encontró el envío.")); }
    private ShippingProviderType provider(String provider) { try { return provider == null ? ShippingProviderType.MANUAL : ShippingProviderType.valueOf(provider); } catch (IllegalArgumentException ex) { return ShippingProviderType.MANUAL; } }

    private ShipmentPackageEstimate estimatePackage(Map<StoreOrderItem, Integer> allocation) {
        BigDecimal weight = new BigDecimal("150");
        BigDecimal width = BigDecimal.ZERO;
        BigDecimal height = BigDecimal.ZERO;
        BigDecimal depth = new BigDecimal("2");
        BigDecimal defaultWeight = new BigDecimal("400");
        BigDecimal defaultWidth = new BigDecimal("17");
        BigDecimal defaultHeight = new BigDecimal("24");
        BigDecimal defaultDepth = new BigDecimal("2.5");

        for (var entry : allocation.entrySet()) {
            var book = entry.getKey().getInventory().getBook();
            int quantity = entry.getValue();
            BigDecimal bookWeight = positive(book.getWeightGrams(), defaultWeight);
            BigDecimal bookWidth = positive(book.getWidthCm(), defaultWidth);
            BigDecimal bookHeight = positive(book.getHeightCm(), defaultHeight);
            BigDecimal bookDepth = positive(book.getDepthCm(), defaultDepth);
            weight = weight.add(bookWeight.multiply(BigDecimal.valueOf(quantity)));
            width = width.max(bookWidth);
            height = height.max(bookHeight);
            depth = depth.add(bookDepth.multiply(BigDecimal.valueOf(quantity)));
        }

        return new ShipmentPackageEstimate(
                weight.setScale(0, RoundingMode.CEILING).intValue(),
                width.add(new BigDecimal("2")).setScale(2, RoundingMode.CEILING),
                height.add(new BigDecimal("2")).setScale(2, RoundingMode.CEILING),
                depth.max(new BigDecimal("5")).setScale(2, RoundingMode.CEILING)
        );
    }

    private BigDecimal positive(BigDecimal value, BigDecimal fallback) {
        return value != null && value.signum() > 0 ? value : fallback;
    }

    private record ShipmentPackageEstimate(Integer weightGrams, BigDecimal widthCm, BigDecimal heightCm, BigDecimal depthCm) {}

    public StoreShipmentResponse toResponse(StoreShipment s) {
        List<StoreShipmentItemResponse> items = shipmentItemRepository.findAllByShipmentIdOrderByIdAsc(s.getId()).stream()
                .map(i -> new StoreShipmentItemResponse(i.getOrderItem().getInventory().getId(), i.getOrderItem().getTitle(), i.getOrderItem().getIsbn(), i.getQuantity())).toList();
        return new StoreShipmentResponse(s.getId(), s.getPackageSequence(), s.getProvider(), s.getServiceCode(), s.getServiceName(), s.getStatus(),
                s.getProviderShipmentId(), s.getTrackingNumber(), s.getTrackingUrl(), s.getLabelUrl(), s.getProviderStatus(), s.getProviderStatusDetail(),
                s.getWeightGrams(), s.getWidthCm(), s.getHeightCm(), s.getDepthCm(), s.getDeclaredValue(), s.getNotes(), s.getCreatedAt(), s.getCreatedAtProvider(),
                s.getDispatchedAt(), s.getDeliveredAt(), s.getCancelledAt(), items, trackingService.events(s.getId()));
    }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
