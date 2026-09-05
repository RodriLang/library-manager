package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobEnqueueCommand;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobEnqueueService;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkAction;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeBulkOperationJdbcRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeBulkOperationJdbcRepository.DispatchItem;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeBulkOperationDispatcherTest {

    @Mock private TiendanubeBulkOperationJdbcRepository bulkRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private TiendanubeProductLinkRepository productLinkRepository;
    @Mock private TiendanubeStoreRepository storeRepository;
    @Mock private TiendanubeJobEnqueueService enqueueService;
    @Mock private Inventory inventory;
    @Mock private Bookstore bookstore;

    @Test
    void publishCreatesBulkJobForUnlinkedInventory() {
        DispatchItem item = new DispatchItem(1L, 10L, 20L, 2L, 30L, 40L, TiendanubeBulkAction.PUBLISH);
        TiendanubeStore store = store();

        when(bulkRepository.claimPendingItems(10)).thenReturn(List.of(item));
        when(inventoryRepository.findAllById(List.of(10L))).thenReturn(List.of(inventory));
        when(inventory.getId()).thenReturn(10L);
        when(inventory.getActive()).thenReturn(true);
        when(inventory.getBookstore()).thenReturn(bookstore);
        when(bookstore.getId()).thenReturn(2L);
        when(productLinkRepository.findAllByInventoryIdInAndActiveTrue(any())).thenReturn(List.of());
        when(storeRepository.findAllById(List.of(30L))).thenReturn(List.of(store));
        when(bulkRepository.findCoalescableJobId(10L, "PUBLISH", 2L, 30L, 40L)).thenReturn(Optional.empty());
        when(enqueueService.enqueue(any())).thenReturn(99L);

        dispatcher().dispatchPendingItems();

        ArgumentCaptor<TiendanubeJobEnqueueCommand> captor = ArgumentCaptor.forClass(TiendanubeJobEnqueueCommand.class);
        verify(enqueueService).enqueue(captor.capture());
        verify(inventory).setTiendanubeStatus(TiendanubeInventoryStatus.PENDING_PUBLICATION);
        verify(bulkRepository).markQueued(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(99L), any());

        assertEquals(TiendanubeJobType.PUBLISH, captor.getValue().type());
        assertEquals(TiendanubeJobSource.BULK, captor.getValue().source());
    }

    @Test
    void unlinkIsLocalAndDoesNotCreateRemoteJob() {
        DispatchItem item = new DispatchItem(1L, 10L, 20L, 2L, 30L, 40L, TiendanubeBulkAction.UNLINK);
        TiendanubeStore store = store();
        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(40L)
                .tiendanubeProductId(50L)
                .tiendanubeVariantId(60L)
                .active(true)
                .build();

        when(bulkRepository.claimPendingItems(10)).thenReturn(List.of(item));
        when(inventoryRepository.findAllById(List.of(10L))).thenReturn(List.of(inventory));
        when(inventory.getId()).thenReturn(10L);
        when(inventory.getActive()).thenReturn(true);
        when(inventory.getBookstore()).thenReturn(bookstore);
        when(bookstore.getId()).thenReturn(2L);
        when(productLinkRepository.findAllByInventoryIdInAndActiveTrue(any())).thenReturn(List.of(link));
        when(storeRepository.findAllById(List.of(30L))).thenReturn(List.of(store));

        dispatcher().dispatchPendingItems();

        verify(enqueueService, never()).enqueue(any());
        verify(inventory).setTiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED);
        verify(inventory).setTiendanubePriceSyncEnabled(false);
        verify(bulkRepository).markCompleted(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    private TiendanubeBulkOperationDispatcher dispatcher() {
        TiendanubeBulkOperationDispatcher dispatcher = new TiendanubeBulkOperationDispatcher(
                bulkRepository,
                inventoryRepository,
                productLinkRepository,
                storeRepository,
                enqueueService
        );
        ReflectionTestUtils.setField(dispatcher, "batchSize", 10);
        return dispatcher;
    }

    private TiendanubeStore store() {
        return TiendanubeStore.builder()
                .id(30L)
                .bookstore(bookstore)
                .storeId(40L)
                .active(true)
                .tokenValid(true)
                .build();
    }
}
