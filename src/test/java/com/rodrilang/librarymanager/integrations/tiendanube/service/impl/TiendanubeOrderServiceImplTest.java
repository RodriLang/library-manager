package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeOrderServiceImplTest {

    @Mock
    private TiendanubeClient client;

    @Mock
    private TiendanubeOrderPersistenceService persistenceService;

    @Test
    void fetchesOrderBeforeDelegatingPaidPersistence() {
        TiendanubeWebhookRequest request = new TiendanubeWebhookRequest(10L, "order/paid", 20L);
        TiendanubeOrderResponse order = new TiendanubeOrderResponse(
                20L,
                List.of(new TiendanubeOrderProductResponse(50L, 30L, "sku", 1, "Libro"))
        );
        when(client.getOrder(10L, 20L)).thenReturn(order);

        new TiendanubeOrderServiceImpl(client, persistenceService).handleOrderPaid(request);

        verify(client).getOrder(10L, 20L);
        verify(persistenceService).applyPaid(request, order);
    }
}
