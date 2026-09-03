package com.rodrilang.librarymanager.integrations.tiendanube.job.handler.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeLinkedInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobConnectionGuard;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobExecutionDataService;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobResultService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeStockJobHandlerTest {

    @Mock
    private TiendanubeJobConnectionGuard connectionGuard;

    @Mock
    private TiendanubeJobExecutionDataService dataService;

    @Mock
    private TiendanubeJobResultService resultService;

    @Mock
    private TiendanubeClient client;

    @Test
    void synchronizesCurrentSnapshotStock() {
        TiendanubeLinkedInventorySnapshot snapshot = new TiendanubeLinkedInventorySnapshot(
                4L, 5L, 3L, 6L, 7L, 12, BigDecimal.TEN, "sku", "sku",
                new TiendanubeUpdateVariantRequest("sku", null, BigDecimal.TEN, 12, true, null, null, null, null)
        );
        TiendanubeJobExecutionContext context = context();
        when(dataService.prepareLinkedInventory(4L, 3L)).thenReturn(Optional.of(snapshot));

        new TiendanubeStockJobHandler(connectionGuard, dataService, resultService, client).execute(context);

        verify(connectionGuard).validate(context);
        verify(client).updateStock(3L, 6L, 7L, 12);
        verify(resultService).registerLinkedSuccess(snapshot);
    }

    private TiendanubeJobExecutionContext context() {
        return new TiendanubeJobExecutionContext(
                1L, 10L, 1, 7, 2L, 30L, 3L, 4L,
                TiendanubeJobType.SYNC_STOCK, TiendanubeJobSource.AUTOMATIC, UUID.randomUUID()
        );
    }
}
