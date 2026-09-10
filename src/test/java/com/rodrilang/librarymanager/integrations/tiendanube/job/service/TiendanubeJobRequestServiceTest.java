package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobEnqueueCommand;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeJobRequestServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private TiendanubeStoreRepository storeRepository;

    @Mock
    private TiendanubeProductLinkRepository productLinkRepository;

    @Mock
    private TiendanubeJobEnqueueService enqueueService;

    @Mock
    private Inventory inventory;

    @Mock
    private Bookstore bookstore;

    @Test
    void manualPublishUsesDefaultRetryPolicyAfterStage3() {
        TiendanubeStore store = TiendanubeStore.builder()
                .id(30L)
                .storeId(3L)
                .active(true)
                .tokenValid(true)
                .build();

        when(inventoryRepository.findById(4L)).thenReturn(Optional.of(inventory));
        when(inventory.getBookstore()).thenReturn(bookstore);
        when(bookstore.getId()).thenReturn(2L);
        when(storeRepository.findByBookstoreIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        when(productLinkRepository.findByInventoryIdAndActiveTrue(4L)).thenReturn(Optional.empty());
        when(enqueueService.enqueue(org.mockito.ArgumentMatchers.any())).thenReturn(100L);

        TiendanubeJobRequestService service = service();
        Long jobId = service.enqueueManualPublish(4L);

        ArgumentCaptor<TiendanubeJobEnqueueCommand> captor = ArgumentCaptor.forClass(TiendanubeJobEnqueueCommand.class);
        verify(enqueueService).enqueue(captor.capture());
        verify(inventory).setTiendanubeStatus(TiendanubeInventoryStatus.PENDING_PUBLICATION);

        assertEquals(100L, jobId);
        assertEquals(30L, captor.getValue().tiendanubeStoreId());
        assertEquals(3L, captor.getValue().storeId());
        assertEquals(TiendanubeJobType.PUBLISH, captor.getValue().type());
        assertEquals(TiendanubeJobSource.MANUAL, captor.getValue().source());
        assertNull(captor.getValue().maxAttempts());
    }

    private TiendanubeJobRequestService service() {
        return new TiendanubeJobRequestService(
                inventoryRepository, storeRepository, productLinkRepository, enqueueService
        );
    }
}
