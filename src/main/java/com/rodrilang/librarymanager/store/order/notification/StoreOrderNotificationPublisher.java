package com.rodrilang.librarymanager.store.order.notification;

import com.rodrilang.librarymanager.store.model.StoreDomainStatus;
import com.rodrilang.librarymanager.store.model.StoreDomainType;
import com.rodrilang.librarymanager.store.order.event.StoreOrderNotificationEvent;
import com.rodrilang.librarymanager.store.order.event.StoreOrderNotificationType;
import com.rodrilang.librarymanager.store.order.model.StoreOrder;
import com.rodrilang.librarymanager.store.order.model.StoreOrderItem;
import com.rodrilang.librarymanager.store.repository.StoreDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StoreOrderNotificationPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final StoreDomainRepository domainRepository;

    @Value("${app.storefront.base-domain:anaquel.com.ar}")
    private String baseDomain;

    @Value("${app.storefront.public-scheme:https}")
    private String publicScheme;

    public void publish(StoreOrderNotificationType type, StoreOrder order, List<StoreOrderItem> items) {
        if (order == null || order.getCustomerEmail() == null || order.getCustomerEmail().isBlank()) {
            return;
        }

        List<StoreOrderNotificationEvent.Item> eventItems = items == null
                ? List.of()
                : items.stream()
                .map(item -> new StoreOrderNotificationEvent.Item(
                        item.getTitle(),
                        item.getIsbn(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .toList();

        eventPublisher.publishEvent(new StoreOrderNotificationEvent(
                type,
                order.getId(),
                order.getOrderNumber(),
                order.getStore().getDisplayName(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getTotal(),
                buildTrackingUrl(order),
                order.getCancellationReason(),
                eventItems
        ));
    }

    private String buildTrackingUrl(StoreOrder order) {
        String hostname = resolvePublicHostname(order);
        String scheme = publicScheme == null || publicScheme.isBlank() ? "https" : publicScheme.trim();

        return UriComponentsBuilder
                .fromUriString(scheme + "://" + hostname)
                .pathSegment("pedido", order.getOrderNumber())
                .queryParam("token", order.getTrackingToken())
                .build()
                .encode()
                .toUriString();
    }

    private String resolvePublicHostname(StoreOrder order) {
        var domains = domainRepository.findAllByStoreIdOrderByIdAsc(order.getStore().getId());

        return domains.stream()
                .filter(domain -> domain.getStatus() == StoreDomainStatus.ACTIVE)
                .filter(domain -> domain.getType() == StoreDomainType.CUSTOM)
                .map(domain -> domain.getHostname())
                .findFirst()
                .orElseGet(() -> domains.stream()
                        .filter(domain -> domain.getStatus() == StoreDomainStatus.ACTIVE)
                        .filter(domain -> domain.getType() == StoreDomainType.ANAQUEL_SUBDOMAIN)
                        .map(domain -> domain.getHostname())
                        .findFirst()
                        .orElse(order.getStore().getSlug() + "." + normalizeBaseDomain()));
    }

    private String normalizeBaseDomain() {
        String value = baseDomain == null ? "anaquel.com.ar" : baseDomain.trim();
        while (value.startsWith(".")) value = value.substring(1);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value.isBlank() ? "anaquel.com.ar" : value;
    }
}
