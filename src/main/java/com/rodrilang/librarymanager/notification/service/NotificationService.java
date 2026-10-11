package com.rodrilang.librarymanager.notification.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.notification.dto.NotificationResponse;
import com.rodrilang.librarymanager.notification.dto.UnreadNotificationCountResponse;
import com.rodrilang.librarymanager.notification.event.NotificationCreatedEvent;
import com.rodrilang.librarymanager.notification.model.*;
import com.rodrilang.librarymanager.notification.repository.NotificationRepository;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.store.order.model.StoreOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");

    private final NotificationRepository repository;
    private final BookstoreRepository bookstoreRepository;
    private final TiendanubeStoreRepository tiendanubeStoreRepository;
    private final BookstoreContext bookstoreContext;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Notification notifyStoreOrderCreated(StoreOrder order) {
        if (order == null || order.getId() == null || order.getBookstore() == null || order.getBookstore().getId() == null) {
            return null;
        }
        String message = "Pedido #" + order.getOrderNumber() + " por " + formatMoney(order.getTotal());
        return createIfAbsent(
                order.getBookstore().getId(),
                NotificationType.STORE_ORDER_CREATED,
                NotificationSource.ANAQUEL_STORE,
                "Nuevo pedido en Anaquel Store",
                message,
                NotificationEntityType.STORE_ORDER,
                order.getId()
        );
    }

    @Transactional
    public Notification notifyTiendanubeSaleCreated(Long externalStoreId, Long orderId) {
        if (externalStoreId == null || orderId == null) return null;
        TiendanubeStore store = tiendanubeStoreRepository.findByStoreId(externalStoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la tienda de Tiendanube vinculada."));
        return createIfAbsent(
                store.getBookstore().getId(),
                NotificationType.TIENDANUBE_SALE_CREATED,
                NotificationSource.TIENDANUBE,
                "Nueva venta en Tiendanube",
                "Pedido #" + orderId + " recibido desde Tiendanube",
                NotificationEntityType.TIENDANUBE_ORDER,
                orderId
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Pageable safe = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"))
                : pageable;
        return PageResponse.of(repository.findAllByBookstoreId(bookstoreId, safe).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse unreadCount() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return new UnreadNotificationCountResponse(repository.countByBookstoreIdAndReadAtIsNull(bookstoreId));
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Notification notification = repository.findByIdAndBookstoreId(notificationId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la notificación."));
        if (notification.getReadAt() == null) notification.setReadAt(Instant.now());
        return toResponse(notification);
    }

    @Transactional
    public UnreadNotificationCountResponse markAllRead() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        repository.markAllRead(bookstoreId, Instant.now());
        return new UnreadNotificationCountResponse(0);
    }

    private Notification createIfAbsent(Long bookstoreId,
                                        NotificationType type,
                                        NotificationSource source,
                                        String title,
                                        String message,
                                        NotificationEntityType entityType,
                                        Long entityId) {
        Notification existing = repository
                .findByBookstoreIdAndTypeAndSourceAndEntityTypeAndEntityId(bookstoreId, type, source, entityType, entityId)
                .orElse(null);
        if (existing != null) return existing;

        Notification saved = repository.save(Notification.builder()
                .bookstore(bookstoreRepository.getReferenceById(bookstoreId))
                .type(type)
                .source(source)
                .title(title)
                .message(message)
                .entityType(entityType)
                .entityId(entityId)
                .build());
        eventPublisher.publishEvent(new NotificationCreatedEvent(saved.getId(), bookstoreId));
        return saved;
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getSource(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private String formatMoney(BigDecimal value) {
        NumberFormat format = NumberFormat.getIntegerInstance(ARGENTINA);
        return "$ " + format.format(value == null ? BigDecimal.ZERO : value);
    }
}
