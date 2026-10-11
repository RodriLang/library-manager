package com.rodrilang.librarymanager.notification.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.notification.event.NotificationCreatedEvent;
import com.rodrilang.librarymanager.notification.model.*;
import com.rodrilang.librarymanager.notification.repository.NotificationRepository;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.store.order.model.StoreOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository repository;
    @Mock BookstoreRepository bookstoreRepository;
    @Mock TiendanubeStoreRepository tiendanubeStoreRepository;
    @Mock BookstoreContext bookstoreContext;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock NotificationPreferenceService preferenceService;
    @Mock NotificationDeliveryOutboxService outboxService;

    @Test
    void storeOrderCreatesNotificationOnce() {
        Bookstore bookstore = mock(Bookstore.class);
        when(bookstore.getId()).thenReturn(7L);
        StoreOrder order = mock(StoreOrder.class);
        when(order.getId()).thenReturn(44L);
        when(order.getBookstore()).thenReturn(bookstore);
        when(order.getOrderNumber()).thenReturn("AQL-44");
        when(order.getTotal()).thenReturn(new BigDecimal("42800"));
        when(repository.findByBookstoreIdAndTypeAndSourceAndEntityTypeAndEntityId(
                7L, NotificationType.STORE_ORDER_CREATED, NotificationSource.ANAQUEL_STORE,
                NotificationEntityType.STORE_ORDER, 44L)).thenReturn(Optional.empty());
        when(bookstoreRepository.getReferenceById(7L)).thenReturn(bookstore);
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(99L);
            return n;
        });

        when(preferenceService.resolve(7L, NotificationType.STORE_ORDER_CREATED))
                .thenReturn(new NotificationPreferenceService.ResolvedPreference(true, false, null));

        service().notifyStoreOrderCreated(order);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.STORE_ORDER_CREATED);
        assertThat(captor.getValue().getSource()).isEqualTo(NotificationSource.ANAQUEL_STORE);
        assertThat(captor.getValue().getEntityId()).isEqualTo(44L);
        verify(eventPublisher).publishEvent(new NotificationCreatedEvent(99L, 7L));
    }


    @Test
    void emailOnlyPreferenceQueuesEmailWithoutPublishingSseEvent() {
        Bookstore bookstore = mock(Bookstore.class);
        when(bookstore.getId()).thenReturn(7L);
        StoreOrder order = mock(StoreOrder.class);
        when(order.getId()).thenReturn(45L);
        when(order.getBookstore()).thenReturn(bookstore);
        when(order.getOrderNumber()).thenReturn("AQL-45");
        when(order.getTotal()).thenReturn(new BigDecimal("12000"));
        when(repository.findByBookstoreIdAndTypeAndSourceAndEntityTypeAndEntityId(
                7L, NotificationType.STORE_ORDER_CREATED, NotificationSource.ANAQUEL_STORE,
                NotificationEntityType.STORE_ORDER, 45L)).thenReturn(Optional.empty());
        when(bookstoreRepository.getReferenceById(7L)).thenReturn(bookstore);
        when(preferenceService.resolve(7L, NotificationType.STORE_ORDER_CREATED))
                .thenReturn(new NotificationPreferenceService.ResolvedPreference(false, true, "ventas@libreria.com"));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(100L);
            return n;
        });

        service().notifyStoreOrderCreated(order);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isInAppVisible()).isFalse();
        verify(eventPublisher, never()).publishEvent(any(NotificationCreatedEvent.class));
        verify(outboxService).enqueueEmail(captor.getValue(), "ventas@libreria.com");
    }

    @Test
    void existingBusinessEventDoesNotCreateDuplicate() {
        Notification existing = Notification.builder().id(1L).build();
        when(repository.findByBookstoreIdAndTypeAndSourceAndEntityTypeAndEntityId(
                7L, NotificationType.TIENDANUBE_SALE_CREATED, NotificationSource.TIENDANUBE,
                NotificationEntityType.TIENDANUBE_ORDER, 55L)).thenReturn(Optional.of(existing));
        Bookstore bookstore = mock(Bookstore.class);
        when(bookstore.getId()).thenReturn(7L);
        TiendanubeStore store = mock(TiendanubeStore.class);
        when(store.getBookstore()).thenReturn(bookstore);
        when(tiendanubeStoreRepository.findByStoreId(10L)).thenReturn(Optional.of(store));

        service().notifyTiendanubeSaleCreated(10L, 55L);

        verify(repository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void listIsScopedToCurrentBookstore() {
        when(bookstoreContext.getCurrentBookstoreId()).thenReturn(8L);
        when(repository.findAllByBookstoreIdAndInAppVisibleTrue(eq(8L), any())).thenReturn(new PageImpl<>(List.of()));

        service().list(PageRequest.of(0, 20));

        verify(repository).findAllByBookstoreIdAndInAppVisibleTrue(eq(8L), any());
        verify(repository, never()).findAllByBookstoreIdAndInAppVisibleTrue(eq(7L), any());
    }

    @Test
    void markReadCannotLoadNotificationFromAnotherBookstore() {
        when(bookstoreContext.getCurrentBookstoreId()).thenReturn(8L);
        when(repository.findByIdAndBookstoreIdAndInAppVisibleTrue(15L, 8L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().markRead(15L))
                .isInstanceOf(com.rodrilang.librarymanager.exception.ResourceNotFoundException.class);

        verify(repository).findByIdAndBookstoreIdAndInAppVisibleTrue(15L, 8L);
    }

    @Test
    void unreadCountAndReadAllAreScopedToCurrentBookstore() {
        when(bookstoreContext.getCurrentBookstoreId()).thenReturn(3L);
        when(repository.countByBookstoreIdAndInAppVisibleTrueAndReadAtIsNull(3L)).thenReturn(4L);

        assertThat(service().unreadCount().unreadCount()).isEqualTo(4L);
        assertThat(service().markAllRead().unreadCount()).isZero();

        verify(repository).markAllRead(eq(3L), any(Instant.class));
    }

    private NotificationService service() {
        return new NotificationService(
                repository,
                bookstoreRepository,
                tiendanubeStoreRepository,
                bookstoreContext,
                eventPublisher,
                preferenceService,
                outboxService
        );
    }
}
