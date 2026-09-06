package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.enums.TiendanubeWebhookEventStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.repository.TiendanubeWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeWebhookServiceImplTest {

    @Mock
    private TiendanubeStoreRepository storeRepository;

    @Mock
    private TiendanubeWebhookEventRepository webhookEventRepository;

    @Test
    void persistsProcessableOrderEventAsPending() {
        String payload = "{\"store_id\":10,\"event\":\"order/paid\",\"id\":20}";
        TiendanubeStore store = TiendanubeStore.builder().id(30L).storeId(10L).build();
        when(storeRepository.findByStoreId(10L)).thenReturn(Optional.of(store));
        when(webhookEventRepository.insert(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(40L);

        service().accept(payload);

        verify(webhookEventRepository).insert(
                eq(30L),
                eq(10L),
                eq("order/paid"),
                eq(20L),
                eq(payload),
                eq(TiendanubeWebhookEventStatus.PENDING),
                eq(1),
                any(Instant.class),
                isNull(),
                isNull()
        );
    }

    @Test
    void persistsUnsupportedEventAsIgnored() {
        String payload = "{\"store_id\":10,\"event\":\"product/updated\",\"id\":20}";
        when(storeRepository.findByStoreId(10L)).thenReturn(Optional.empty());
        when(webhookEventRepository.insert(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(41L);

        service().accept(payload);

        verify(webhookEventRepository).insert(
                isNull(),
                eq(10L),
                eq("product/updated"),
                eq(20L),
                eq(payload),
                eq(TiendanubeWebhookEventStatus.IGNORED),
                eq(1),
                any(Instant.class),
                isNull(),
                isNull()
        );
    }

    private TiendanubeWebhookServiceImpl service() {
        return new TiendanubeWebhookServiceImpl(
                new ObjectMapper(),
                storeRepository,
                webhookEventRepository
        );
    }
}
