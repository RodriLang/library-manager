package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.repository.TiendanubeProcessedEventClaimRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeOrderPersistenceServiceTest {

    @Mock
    private TiendanubeProductLinkRepository productLinkRepository;

    @Mock
    private TiendanubeProcessedEventClaimRepository processedEventClaimRepository;

    @Mock
    private InventoryService inventoryService;

    @Test
    void duplicateCreatedEventDoesNotApplyStockTwice() {
        TiendanubeWebhookRequest request = new TiendanubeWebhookRequest(10L, "order/created", 20L);
        TiendanubeOrderResponse order = order();
        when(processedEventClaimRepository.tryClaimOrderStockDeduction(eq(10L), eq(20L), any(Instant.class)))
                .thenReturn(false);

        service().applyCreated(request, order);

        verify(inventoryService, never()).recordTiendanubeSale(any(), any(), any());
        verify(productLinkRepository, never()).findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(any(), any());
    }

    @Test
    void createdEventClaimsBusinessEventAndAppliesStock() {
        TiendanubeWebhookRequest request = new TiendanubeWebhookRequest(10L, "order/created", 20L);
        TiendanubeOrderResponse order = order();
        Inventory inventory = org.mockito.Mockito.mock(Inventory.class);
        TiendanubeProductLink link = org.mockito.Mockito.mock(TiendanubeProductLink.class);

        when(processedEventClaimRepository.tryClaimOrderStockDeduction(eq(10L), eq(20L), any(Instant.class)))
                .thenReturn(true);
        when(productLinkRepository.findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(10L, 30L))
                .thenReturn(Optional.of(link));
        when(link.getInventory()).thenReturn(inventory);
        when(inventory.getId()).thenReturn(40L);

        service().applyCreated(request, order);

        verify(inventoryService).recordTiendanubeSale(40L, 2, "20");
    }

    @Test
    void paidEventIsOnlyFallbackWhenCreatedWasNotApplied() {
        TiendanubeWebhookRequest request = new TiendanubeWebhookRequest(10L, "order/paid", 20L);
        TiendanubeOrderResponse order = order();
        when(processedEventClaimRepository.tryClaimOrderStockDeduction(eq(10L), eq(20L), any(Instant.class)))
                .thenReturn(false);

        service().applyPaidFallback(request, order);

        verify(inventoryService, never()).recordTiendanubeSale(any(), any(), any());
    }

    private TiendanubeOrderPersistenceService service() {
        return new TiendanubeOrderPersistenceService(
                productLinkRepository,
                processedEventClaimRepository,
                inventoryService
        );
    }

    private TiendanubeOrderResponse order() {
        return new TiendanubeOrderResponse(
                20L,
                List.of(new TiendanubeOrderProductResponse(50L, 30L, "9780000000000", 2, "Libro"))
        );
    }
}
