package com.rodrilang.librarymanager.notification.service;

import com.rodrilang.librarymanager.notification.dto.NotificationResponse;
import com.rodrilang.librarymanager.notification.model.NotificationEntityType;
import com.rodrilang.librarymanager.notification.model.NotificationSource;
import com.rodrilang.librarymanager.notification.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSseServiceTest {

    @Test
    void subscriptionIsScopedToBookstoreAndCanReceiveEvents() {
        NotificationSseService service = new NotificationSseService();
        SseEmitter first = service.subscribe(1L);
        SseEmitter second = service.subscribe(2L);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(service.connectionCount(1L)).isEqualTo(1);
        assertThat(service.connectionCount(2L)).isEqualTo(1);

        service.publish(1L, new NotificationResponse(
                10L,
                NotificationType.STORE_ORDER_CREATED,
                NotificationSource.ANAQUEL_STORE,
                "Nuevo pedido",
                "Pedido #10",
                NotificationEntityType.STORE_ORDER,
                10L,
                false,
                null,
                Instant.now()
        ));

        assertThat(service.connectionCount(1L)).isEqualTo(1);
        assertThat(service.connectionCount(2L)).isEqualTo(1);

        first.complete();
        second.complete();
    }
}
