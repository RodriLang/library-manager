package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeWebhookResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeWebhookRegistrationServiceTest {

    private static final String URL = "https://api-dev.anaquel.com.ar/api/integrations/tiendanube/webhooks";

    @Mock
    private TiendanubeClient client;

    @Test
    void createsOnlyMissingWebhooks() {
        when(client.getWebhooks(10L)).thenReturn(List.of(
                new TiendanubeWebhookResponse(1L, "order/created", URL),
                new TiendanubeWebhookResponse(2L, "order/cancelled", URL)
        ));

        new TiendanubeWebhookRegistrationService(client, URL).ensureRegistered(10L);

        verify(client).createWebhook(10L, "order/paid", URL);
        verify(client, never()).createWebhook(10L, "order/created", URL);
        verify(client, never()).createWebhook(10L, "order/cancelled", URL);
    }

    @Test
    void acceptsTrailingSlashAsSameUrl() {
        when(client.getWebhooks(10L)).thenReturn(List.of(
                new TiendanubeWebhookResponse(1L, "order/created", URL + "/"),
                new TiendanubeWebhookResponse(2L, "order/paid", URL),
                new TiendanubeWebhookResponse(3L, "order/cancelled", URL)
        ));

        new TiendanubeWebhookRegistrationService(client, URL).ensureRegistered(10L);

        verify(client, never()).createWebhook(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsNonHttpsWebhookUrl() {
        TiendanubeWebhookRegistrationService service = new TiendanubeWebhookRegistrationService(
                client,
                "http://localhost:8080/api/integrations/tiendanube/webhooks"
        );

        assertThrows(IllegalStateException.class, () -> service.ensureRegistered(10L));
        verify(client, never()).getWebhooks(10L);
    }
}
