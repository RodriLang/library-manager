package com.rodrilang.librarymanager.integrations.tiendanube.job.handler.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublicationSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublishSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobConnectionGuard;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobExecutionDataService;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobResultService;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubePublicationSyncService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductMatchingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubePublishJobHandlerTest {

    @Mock
    private TiendanubeJobConnectionGuard connectionGuard;

    @Mock
    private TiendanubeJobExecutionDataService dataService;

    @Mock
    private TiendanubeJobResultService resultService;

    @Mock
    private TiendanubeProductMatchingService matchingService;

    @Mock
    private TiendanubePublicationSyncService publicationSyncService;

    @Mock
    private TiendanubeClient client;

    @Mock
    private TiendanubePublicationSnapshot publicationSnapshot;

    @Test
    void retryRecoversProductByStableSkuWithoutCreatingDuplicate() {
        TiendanubePublishSnapshot snapshot = snapshot();
        TiendanubeProductResponse remote = product(50L, 60L, "9789500000001");
        when(dataService.preparePublish(4L, 3L)).thenReturn(Optional.of(snapshot));
        when(client.findProductBySku(3L, "9789500000001")).thenReturn(Optional.of(remote));
        when(dataService.preparePublication(4L, 3L)).thenReturn(Optional.of(publicationSnapshot));

        handler().execute(context());

        verify(resultService).saveExistingLink(4L, 3L, 50L, remote.variants().getFirst(), "9789500000001");
        verify(publicationSyncService).sync(publicationSnapshot);
        verify(client, never()).createProduct(any(), any());
        verify(client, never()).getProducts(any());
    }

    @Test
    void createsProductWithoutImagesAndThenCompletesPublicationSync() {
        TiendanubePublishSnapshot snapshot = snapshot();
        TiendanubeProductResponse created = product(50L, 60L, "9789500000001");
        when(dataService.preparePublish(4L, 3L)).thenReturn(Optional.of(snapshot));
        when(client.findProductBySku(3L, "9789500000001")).thenReturn(Optional.empty());
        when(client.searchProducts(3L, "Libro - Autor")).thenReturn(List.of());
        when(matchingService.findRemoteMatch("9789500000001", "libro", List.of())).thenReturn(null);
        when(client.createProduct(eq(3L), any(TiendanubeCreateProductRequest.class))).thenReturn(created);
        when(dataService.preparePublication(4L, 3L)).thenReturn(Optional.of(publicationSnapshot));

        handler().execute(context());

        ArgumentCaptor<TiendanubeCreateProductRequest> requestCaptor =
                ArgumentCaptor.forClass(TiendanubeCreateProductRequest.class);
        verify(client).createProduct(eq(3L), requestCaptor.capture());
        assertTrue(requestCaptor.getValue().images().isEmpty());
        verify(resultService).savePublishedLink(4L, 3L, created, created.variants().getFirst(), null, "9789500000001");
        verify(publicationSyncService).sync(publicationSnapshot);
        verify(client, never()).getProducts(any());
    }

    @Test
    void retryWithExistingLinkContinuesPublicationSyncInsteadOfFinishingEarly() {
        when(dataService.preparePublish(4L, 3L)).thenReturn(Optional.empty());
        when(dataService.preparePublication(4L, 3L)).thenReturn(Optional.of(publicationSnapshot));

        handler().execute(context());

        verify(publicationSyncService).sync(publicationSnapshot);
        verify(client, never()).createProduct(any(), any());
    }

    private TiendanubePublishJobHandler handler() {
        return new TiendanubePublishJobHandler(
                connectionGuard, dataService, resultService, matchingService, publicationSyncService, client
        );
    }

    private TiendanubeJobExecutionContext context() {
        return new TiendanubeJobExecutionContext(
                1L, 2L, 1, 7, 10L, 30L, 3L, 4L,
                TiendanubeJobType.PUBLISH, TiendanubeJobSource.MANUAL, UUID.randomUUID()
        );
    }

    private TiendanubePublishSnapshot snapshot() {
        TiendanubeCreateProductRequest productRequest = new TiendanubeCreateProductRequest(
                Map.of("es", "Libro - Autor"),
                Map.of(),
                List.of(),
                List.of(new TiendanubeCreateImageRequest("https://cover", 1)),
                true
        );
        TiendanubeUpdateVariantRequest variantRequest = new TiendanubeUpdateVariantRequest(
                "9789500000001", "9789500000001", BigDecimal.valueOf(1000), 2, true,
                null, null, null, null
        );

        return new TiendanubePublishSnapshot(
                4L, 10L, 3L, "9789500000001", "libro", "https://cover", productRequest, variantRequest
        );
    }

    private TiendanubeProductResponse product(Long productId, Long variantId, String sku) {
        TiendanubeVariantResponse variant = new TiendanubeVariantResponse(
                variantId, sku, sku, BigDecimal.valueOf(1000), 2
        );
        return new TiendanubeProductResponse(
                productId, Map.of("es", "Libro - Autor"), Map.of(), true, List.of(variant), List.of()
        );
    }
}
