package com.rodrilang.librarymanager.shipping.shipment.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.quote.model.StoreShippingQuote;
import com.rodrilang.librarymanager.shipping.quote.repository.StoreShippingQuoteRepository;
import com.rodrilang.librarymanager.shipping.shipment.dto.StoreShipmentResponse;
import com.rodrilang.librarymanager.shipping.shipment.dto.UpdateShipmentMetadataRequest;
import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import com.rodrilang.librarymanager.shipping.shipment.repository.StoreShipmentRepository;
import com.rodrilang.librarymanager.store.order.model.StoreDeliveryMethod;
import com.rodrilang.librarymanager.store.order.model.StoreFulfillmentStatus;
import com.rodrilang.librarymanager.store.order.model.StoreOrder;
import com.rodrilang.librarymanager.store.order.model.StoreOrderStatus;
import com.rodrilang.librarymanager.store.order.repository.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreShipmentService {
    private final BookstoreContext bookstoreContext;
    private final StoreOrderRepository orderRepository;
    private final StoreShipmentRepository shipmentRepository;
    private final StoreShippingQuoteRepository quoteRepository;

    @Transactional(readOnly = true)
    public List<StoreShipmentResponse> list(Long orderId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        requireOrder(orderId, bookstoreId);
        return shipmentRepository.findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(orderId, bookstoreId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public StoreShipmentResponse createDraft(Long orderId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        StoreOrder order = requireOrder(orderId, bookstoreId);
        if (order.getDeliveryMethod() != StoreDeliveryMethod.SHIPPING) {
            throw new BusinessException("Este pedido es para retiro en librería y no requiere un envío.");
        }
        if (order.getStatus() != StoreOrderStatus.CONFIRMED) {
            throw new BusinessException("Confirmá el pedido antes de preparar el envío.");
        }
        List<StoreShipment> existing = shipmentRepository.findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(orderId, bookstoreId);
        StoreShipment active = existing.stream()
                .filter(s -> s.getStatus() != ShipmentStatus.CANCELLED)
                .findFirst().orElse(null);
        if (active != null) return toResponse(active);

        StoreShippingQuote quote = findQuote(order.getShippingQuoteId());
        StoreShipment shipment = StoreShipment.builder()
                .bookstore(order.getBookstore())
                .order(order)
                .provider(provider(order.getShippingProvider()))
                .serviceCode(order.getShippingServiceCode())
                .serviceName(order.getShippingServiceName())
                .status(ShipmentStatus.DRAFT)
                .weightGrams(quote != null ? quote.getWeightGrams() : null)
                .widthCm(quote != null ? quote.getWidthCm() : null)
                .heightCm(quote != null ? quote.getHeightCm() : null)
                .depthCm(quote != null ? quote.getDepthCm() : null)
                .declaredValue(order.getSubtotal())
                .build();
        return toResponse(shipmentRepository.save(shipment));
    }

    @Transactional
    public StoreShipmentResponse updateMetadata(Long orderId, Long shipmentId, UpdateShipmentMetadataRequest request) {
        StoreShipment shipment = requireShipment(orderId, shipmentId);
        shipment.setProviderShipmentId(normalize(request.providerShipmentId()));
        shipment.setTrackingNumber(normalize(request.trackingNumber()));
        shipment.setTrackingUrl(normalize(request.trackingUrl()));
        shipment.setLabelUrl(normalize(request.labelUrl()));
        shipment.setProviderStatus(normalize(request.providerStatus()));
        shipment.setProviderStatusDetail(normalize(request.providerStatusDetail()));
        if (shipment.getProviderShipmentId() != null && shipment.getCreatedAtProvider() == null) {
            shipment.setCreatedAtProvider(Instant.now());
        }
        if (shipment.getStatus() == ShipmentStatus.DRAFT && shipment.getTrackingNumber() != null) {
            shipment.setStatus(ShipmentStatus.READY_TO_SHIP);
        }
        return toResponse(shipment);
    }

    @Transactional
    public StoreShipmentResponse markDispatched(Long orderId, Long shipmentId) {
        StoreShipment shipment = requireShipment(orderId, shipmentId);
        if (shipment.getStatus() == ShipmentStatus.CANCELLED || shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new BusinessException("El envío ya no puede marcarse como despachado.");
        }
        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        shipment.setDispatchedAt(Instant.now());
        shipment.getOrder().setFulfillmentStatus(StoreFulfillmentStatus.SHIPPED);
        return toResponse(shipment);
    }

    @Transactional
    public StoreShipmentResponse markDelivered(Long orderId, Long shipmentId) {
        StoreShipment shipment = requireShipment(orderId, shipmentId);
        if (shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new BusinessException("Un envío cancelado no puede marcarse como entregado.");
        }
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(Instant.now());
        shipment.getOrder().setFulfillmentStatus(StoreFulfillmentStatus.DELIVERED);
        return toResponse(shipment);
    }

    @Transactional
    public StoreShipmentResponse cancel(Long orderId, Long shipmentId) {
        StoreShipment shipment = requireShipment(orderId, shipmentId);
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new BusinessException("Un envío entregado no puede cancelarse.");
        }
        shipment.setStatus(ShipmentStatus.CANCELLED);
        shipment.setCancelledAt(Instant.now());
        return toResponse(shipment);
    }

    private StoreOrder requireOrder(Long orderId, Long bookstoreId) {
        return orderRepository.findByIdAndBookstoreId(orderId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido."));
    }

    private StoreShipment requireShipment(Long orderId, Long shipmentId) {
        return shipmentRepository.findByIdAndOrderIdAndBookstoreId(shipmentId, orderId, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el envío."));
    }

    private StoreShippingQuote findQuote(UUID quoteId) {
        return quoteId == null ? null : quoteRepository.findById(quoteId).orElse(null);
    }

    private ShippingProviderType provider(String provider) {
        try {
            return provider == null ? ShippingProviderType.MANUAL : ShippingProviderType.valueOf(provider);
        } catch (IllegalArgumentException ex) {
            return ShippingProviderType.MANUAL;
        }
    }

    private StoreShipmentResponse toResponse(StoreShipment s) {
        return new StoreShipmentResponse(s.getId(), s.getProvider(), s.getServiceCode(), s.getServiceName(), s.getStatus(),
                s.getProviderShipmentId(), s.getTrackingNumber(), s.getTrackingUrl(), s.getLabelUrl(), s.getProviderStatus(),
                s.getProviderStatusDetail(), s.getWeightGrams(), s.getWidthCm(), s.getHeightCm(), s.getDepthCm(), s.getDeclaredValue(),
                s.getCreatedAt(), s.getCreatedAtProvider(), s.getDispatchedAt(), s.getDeliveredAt(), s.getCancelledAt());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
