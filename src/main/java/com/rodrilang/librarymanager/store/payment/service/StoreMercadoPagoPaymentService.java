package com.rodrilang.librarymanager.store.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.store.model.StoreDomainStatus;
import com.rodrilang.librarymanager.store.model.StoreDomainType;
import com.rodrilang.librarymanager.store.order.event.StoreOrderNotificationType;
import com.rodrilang.librarymanager.store.order.model.*;
import com.rodrilang.librarymanager.store.order.notification.StoreOrderNotificationPublisher;
import com.rodrilang.librarymanager.store.order.repository.StoreOrderItemRepository;
import com.rodrilang.librarymanager.store.order.repository.StoreOrderRepository;
import com.rodrilang.librarymanager.store.order.repository.StoreStockReservationRepository;
import com.rodrilang.librarymanager.store.payment.client.MercadoPagoClient;
import com.rodrilang.librarymanager.store.repository.StoreDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StoreMercadoPagoPaymentService {
    private final StoreMercadoPagoConfigService configService;
    private final MercadoPagoClient client;
    private final StoreOrderRepository orderRepository;
    private final StoreOrderItemRepository itemRepository;
    private final StoreStockReservationRepository reservationRepository;
    private final StoreDomainRepository domainRepository;
    private final StoreOrderNotificationPublisher notificationPublisher;

    @Value("${app.storefront.base-domain:anaquel.com.ar}") private String baseDomain;
    @Value("${app.storefront.public-scheme:https}") private String publicScheme;

    @Transactional
    public void createCheckout(StoreOrder order, List<StoreOrderItem> items) {
        if (order.getPaymentMethod() != StorePaymentMethod.MERCADO_PAGO) return;
        var credentials = configService.requireCredentials(order.getBookstore().getId());
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("type", "online");
        body.put("processing_mode", "manual");
        body.put("capture_mode", "automatic_async");
        body.put("external_reference", order.getOrderNumber());
        body.put("description", "Pedido " + order.getOrderNumber() + " · " + order.getStore().getDisplayName());
        body.put("total_amount", order.getTotal().toPlainString());
        if (order.getReservationExpiresAt() != null) {
            java.time.Duration ttl = java.time.Duration.between(java.time.Instant.now(), order.getReservationExpiresAt());
            if (!ttl.isNegative() && !ttl.isZero()) body.put("expiration_time", ttl.toString());
        }
        body.put("payer", Map.of("email", order.getCustomerEmail()));
        body.put("items", items.stream().map(item -> Map.<String,Object>of(
                "title", item.getTitle(),
                "quantity", item.getQuantity(),
                "unit_price", item.getUnitPrice().toPlainString(),
                "unit_measure", "unit",
                "total_amount", item.getSubtotal().toPlainString()
        )).toList());
        String tracking = trackingUrl(order);
        body.put("config", Map.of("online", Map.of(
                "success_url", tracking + "&payment=success",
                "pending_url", tracking + "&payment=pending",
                "failure_url", tracking + "&payment=failure",
                "auto_return", "approved"
        )));

        JsonNode response = client.createOrder(credentials.accessToken(), order.getClientRequestId().toString(), body);
        if (response == null || response.path("id").asText().isBlank() || response.path("checkout_url").asText().isBlank()) {
            throw new BusinessException("Mercado Pago no devolvió una URL válida para iniciar el pago.");
        }
        order.setPaymentExternalId(response.path("id").asText());
        order.setPaymentCheckoutUrl(response.path("checkout_url").asText());
        order.setPaymentStatusDetail(response.path("status_detail").asText(null));
        order.setPaymentUpdatedAt(Instant.now());
        orderRepository.save(order);
    }

    @Transactional
    public void synchronizeFromWebhook(String externalOrderId) {
        StoreOrder order = orderRepository.findByPaymentExternalIdForUpdate(externalOrderId)
                .orElseThrow(() -> new BusinessException("No se encontró el pago de Mercado Pago asociado."));
        var credentials = configService.requireCredentials(order.getBookstore().getId());
        JsonNode remote = client.getOrder(credentials.accessToken(), externalOrderId);
        applyRemoteStatus(order, remote);
    }

    @Transactional
    public boolean processWebhook(String externalOrderId, String xRequestId, String xSignature) {
        StoreOrder order = orderRepository.findByPaymentExternalIdForUpdate(externalOrderId).orElse(null);
        if (order == null) return true;
        var credentials = configService.requireCredentials(order.getBookstore().getId());
        if (!verifySignature(externalOrderId, xRequestId, xSignature, credentials.webhookSecret())) return false;
        JsonNode remote = client.getOrder(credentials.accessToken(), externalOrderId);
        applyRemoteStatus(order, remote);
        return true;
    }

    private void applyRemoteStatus(StoreOrder order, JsonNode remote) {
        String status = remote.path("status").asText("");
        String detail = remote.path("status_detail").asText(null);
        order.setPaymentStatusDetail(detail);
        order.setPaymentUpdatedAt(Instant.now());

        StorePaymentStatus beforePayment = order.getPaymentStatus();
        StoreOrderStatus beforeOrder = order.getStatus();

        if ("processed".equals(status) && "accredited".equals(detail)) {
            order.setPaymentStatus(StorePaymentStatus.PAID);
            if (order.getStatus() == StoreOrderStatus.PENDING) {
                confirmPaidOrder(order);
            }
        } else if ("refunded".equals(status) || ("processed".equals(status) && "refunded".equals(detail))) {
            order.setPaymentStatus(StorePaymentStatus.REFUNDED);
        } else if ("processed".equals(status) && "partially_refunded".equals(detail)) {
            order.setPaymentStatus(StorePaymentStatus.PARTIALLY_REFUNDED);
        } else if ("failed".equals(status) || "canceled".equals(status) || "expired".equals(status)) {
            if (order.getPaymentStatus() != StorePaymentStatus.PAID) {
                order.setPaymentStatus(StorePaymentStatus.FAILED);
                if (order.getStatus() == StoreOrderStatus.PENDING) {
                    releaseFailedPaymentReservation(order, "expired".equals(status));
                }
            }
        } else {
            if (order.getPaymentStatus() != StorePaymentStatus.PAID) order.setPaymentStatus(StorePaymentStatus.PENDING);
        }
        orderRepository.save(order);

        List<StoreOrderItem> notificationItems = null;
        if (beforeOrder != StoreOrderStatus.CONFIRMED && order.getStatus() == StoreOrderStatus.CONFIRMED) {
            notificationItems = itemRepository.findAllByOrderIdOrderByIdAsc(order.getId());
            notificationPublisher.publish(StoreOrderNotificationType.CONFIRMED, order, notificationItems);
        } else if (beforeOrder == StoreOrderStatus.PENDING
                && (order.getStatus() == StoreOrderStatus.CANCELLED || order.getStatus() == StoreOrderStatus.EXPIRED)) {
            notificationItems = itemRepository.findAllByOrderIdOrderByIdAsc(order.getId());
            notificationPublisher.publish(StoreOrderNotificationType.CANCELLED, order, notificationItems);
        }
    }

    private void releaseFailedPaymentReservation(StoreOrder order, boolean expired) {
        Instant now = Instant.now();
        List<StoreStockReservation> reservations = reservationRepository.findAllByOrderIdOrderByIdAsc(order.getId());
        for (StoreStockReservation reservation : reservations) {
            if (reservation.getStatus() == StoreReservationStatus.ACTIVE) {
                reservation.setStatus(expired ? StoreReservationStatus.EXPIRED : StoreReservationStatus.RELEASED);
                reservation.setReleasedAt(now);
                reservation.setExpiresAt(null);
            }
        }
        reservationRepository.saveAll(reservations);
        order.setReservationExpiresAt(null);
        order.setCancelledAt(now);
        order.setCancellationReason(expired ? "El pago de Mercado Pago venció." : "El pago de Mercado Pago no pudo completarse.");
        order.setStatus(expired ? StoreOrderStatus.EXPIRED : StoreOrderStatus.CANCELLED);
    }

    private void confirmPaidOrder(StoreOrder order) {
        Instant now = Instant.now();
        List<StoreStockReservation> reservations = reservationRepository.findAllByOrderIdOrderByIdAsc(order.getId());
        if (reservations.isEmpty() || reservations.stream().anyMatch(r -> r.getStatus() != StoreReservationStatus.ACTIVE
                || (r.getExpiresAt() != null && !r.getExpiresAt().isAfter(now)))) {
            throw new BusinessException("El pago fue acreditado pero la reserva de stock ya no está disponible. Requiere revisión manual.");
        }
        reservations.forEach(r -> r.setExpiresAt(null));
        reservationRepository.saveAll(reservations);
        order.setStatus(StoreOrderStatus.CONFIRMED);
        order.setConfirmedAt(now);
        order.setReservationExpiresAt(null);
    }

    public boolean verifySignature(String externalOrderId, String xRequestId, String xSignature, String secret) {
        if (externalOrderId == null || xRequestId == null || xSignature == null || secret == null) return false;
        Map<String,String> parts = new HashMap<>();
        for (String token : xSignature.split(",")) {
            String[] pair = token.trim().split("=", 2);
            if (pair.length == 2) parts.put(pair[0], pair[1]);
        }
        String ts = parts.get("ts");
        String expected = parts.get("v1");
        if (ts == null || expected == null) return false;
        String manifest = "id:" + externalOrderId.toLowerCase(Locale.ROOT) + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        String actual = hmacSha256(secret, manifest);
        return java.security.MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.UTF_8), expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String hmacSha256(String secret, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("No se pudo validar la firma de Mercado Pago.", e); }
    }

    private String trackingUrl(StoreOrder order) {
        String host = domainRepository.findAllByStoreIdOrderByIdAsc(order.getStore().getId()).stream()
                .filter(d -> d.getStatus() == StoreDomainStatus.ACTIVE && d.getType() == StoreDomainType.CUSTOM)
                .map(d -> d.getHostname()).findFirst()
                .orElseGet(() -> domainRepository.findAllByStoreIdOrderByIdAsc(order.getStore().getId()).stream()
                        .filter(d -> d.getStatus() == StoreDomainStatus.ACTIVE && d.getType() == StoreDomainType.ANAQUEL_SUBDOMAIN)
                        .map(d -> d.getHostname()).findFirst()
                        .orElse(order.getStore().getSlug() + "." + normalizeBaseDomain()));
        String scheme = publicScheme == null || publicScheme.isBlank() ? "https" : publicScheme.trim();
        return UriComponentsBuilder.fromUriString(scheme + "://" + host)
                .pathSegment("pedido", order.getOrderNumber())
                .queryParam("token", order.getTrackingToken()).build().encode().toUriString();
    }

    private String normalizeBaseDomain() {
        String v = baseDomain == null ? "anaquel.com.ar" : baseDomain.trim();
        while (v.startsWith(".")) v = v.substring(1);
        while (v.endsWith(".")) v = v.substring(0, v.length()-1);
        return v.isBlank() ? "anaquel.com.ar" : v;
    }
}
