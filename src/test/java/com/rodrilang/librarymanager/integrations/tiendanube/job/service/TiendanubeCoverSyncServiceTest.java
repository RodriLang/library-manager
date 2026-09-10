package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImageResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeCoverSyncResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeCoverSyncServiceTest {

    @Mock
    private TiendanubeClient client;

    @Mock
    private TiendanubeCoverSyncStateService stateService;

    @Test
    void unchangedCoverDoesNotCallRemoteApi() {
        TiendanubeCoverSyncResult result = service().sync(
                5L, 3L, 50L, "https://cover", "https://cover", 10L, null, null
        );

        assertFalse(result.changed());
        verify(client, never()).getProduct(3L, 50L);
    }

    @Test
    void retryRecoversImageCreatedByPreviousAttemptFromPersistedBaseline() {
        TiendanubeImageResponse previous = new TiendanubeImageResponse(10L, "https://cdn/old", 2);
        TiendanubeImageResponse current = new TiendanubeImageResponse(20L, "https://cdn/new", 1);
        when(client.getProduct(3L, 50L)).thenReturn(product(List.of(current, previous)));

        TiendanubeCoverSyncResult result = service().sync(
                5L, 3L, 50L, "https://source/new", "https://source/old", 10L,
                "https://source/new", "10"
        );

        assertTrue(result.changed());
        assertEquals(20L, result.imageId());
        assertEquals("https://source/new", result.coverUrl());
        verify(client).deleteProductImage(3L, 50L, 10L);
        verify(client, never()).createProductImage(any(), any(), any());
    }

    @Test
    void persistsBaselineBeforeCreatingNewImage() {
        TiendanubeImageResponse previous = new TiendanubeImageResponse(10L, "https://cdn/old", 1);
        TiendanubeImageResponse created = new TiendanubeImageResponse(20L, "https://cdn/new", 1);
        when(client.getProduct(3L, 50L)).thenReturn(product(List.of(previous)));
        when(client.createProductImage(3L, 50L, new TiendanubeCreateImageRequest("https://source/new", 1)))
                .thenReturn(created);

        TiendanubeCoverSyncResult result = service().sync(
                5L, 3L, 50L, "https://source/new", "https://source/old", 10L, null, null
        );

        verify(stateService).begin(5L, "https://source/new", "10");
        verify(client).deleteProductImage(3L, 50L, 10L);
        assertEquals(20L, result.imageId());
    }

    @Test
    void removingLocalCoverRemovesPreviousManagedImageAndClearsSnapshot() {
        TiendanubeCoverSyncResult result = service().sync(
                5L, 3L, 50L, null, "https://old", 10L, null, null
        );

        assertTrue(result.changed());
        assertNull(result.imageId());
        assertNull(result.coverUrl());
        verify(client).deleteProductImage(3L, 50L, 10L);
    }

    private TiendanubeCoverSyncService service() {
        return new TiendanubeCoverSyncService(client, stateService);
    }

    private TiendanubeProductResponse product(List<TiendanubeImageResponse> images) {
        return new TiendanubeProductResponse(50L, Map.of("es", "Libro"), Map.of(), true, List.of(), images);
    }
}
