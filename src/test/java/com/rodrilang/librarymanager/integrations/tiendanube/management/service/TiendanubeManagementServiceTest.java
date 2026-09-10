package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeBulkOperationRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeBulkSelectionRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeInventoryFilterRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeBulkOperationResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.entity.TiendanubeBulkOperation;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminPublicationFilter;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminStockFilter;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkAction;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkOperationStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkSelectionType;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeBulkOperationJdbcRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeBulkOperationRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeManagementInventoryRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeManagementServiceTest {

    @Mock
    private BookstoreContext bookstoreContext;

    @Mock
    private TiendanubeStoreRepository storeRepository;

    @Mock
    private TiendanubeManagementInventoryRepository inventoryRepository;

    @Mock
    private TiendanubeBulkOperationRepository operationRepository;

    @Mock
    private TiendanubeBulkOperationJdbcRepository bulkRepository;

    @Mock
    private TiendanubeWorkNotifier workNotifier;

    @Test
    void filterSelectionIsMaterializedWhenOperationIsCreated() {
        TiendanubeStore store = TiendanubeStore.builder()
                .id(5L)
                .storeId(100L)
                .active(true)
                .tokenValid(true)
                .build();

        TiendanubeInventoryFilterRequest filter = new TiendanubeInventoryFilterRequest(
                "planeta",
                TiendanubeAdminPublicationFilter.NOT_PUBLISHED,
                null,
                TiendanubeAdminStockFilter.ALL,
                null
        );

        TiendanubeBulkOperationRequest request = new TiendanubeBulkOperationRequest(
                TiendanubeBulkAction.PUBLISH,
                new TiendanubeBulkSelectionRequest(
                        TiendanubeBulkSelectionType.FILTER,
                        null,
                        filter
                )
        );

        when(bookstoreContext.getCurrentBookstoreId()).thenReturn(1L);
        when(storeRepository.findByBookstoreIdAndActiveTrue(1L)).thenReturn(Optional.of(store));
        when(inventoryRepository.findMatchingInventoryIds(1L, filter)).thenReturn(List.of(11L, 12L));

        when(operationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TiendanubeBulkOperation operation = invocation.getArgument(0);
            operation.setId(50L);
            return operation;
        });

        when(bulkRepository.findOperation(50L, 1L))
                .thenReturn(Optional.of(response(50L, 2)));

        TiendanubeBulkOperationResponse result = service().createBulkOperation(request);

        verify(bulkRepository).insertItems(
                eq(50L),
                eq(List.of(11L, 12L)),
                any(Instant.class)
        );

        verify(workNotifier).notifyWork(TiendanubeWorkType.BULK);

        assertEquals(50L, result.id());
        assertEquals(2, result.totalItems());
    }

    @Test
    void idSelectionRejectsInventoriesOutsideCurrentBookstore() {
        TiendanubeStore store = TiendanubeStore.builder()
                .id(5L)
                .storeId(100L)
                .active(true)
                .tokenValid(true)
                .build();

        when(bookstoreContext.getCurrentBookstoreId()).thenReturn(1L);
        when(storeRepository.findByBookstoreIdAndActiveTrue(1L)).thenReturn(Optional.of(store));
        when(inventoryRepository.findExistingInventoryIds(1L, Set.of(11L, 99L)))
                .thenReturn(List.of(11L));

        TiendanubeBulkOperationRequest request = new TiendanubeBulkOperationRequest(
                TiendanubeBulkAction.SYNC_STOCK,
                new TiendanubeBulkSelectionRequest(
                        TiendanubeBulkSelectionType.IDS,
                        Set.of(11L, 99L),
                        null
                )
        );

        assertThrows(
                BusinessException.class,
                () -> service().createBulkOperation(request)
        );

        verify(workNotifier, never()).notifyWork(any());
    }

    private TiendanubeManagementService service() {
        return new TiendanubeManagementService(
                bookstoreContext,
                storeRepository,
                inventoryRepository,
                operationRepository,
                bulkRepository,
                workNotifier
        );
    }

    private TiendanubeBulkOperationResponse response(Long id, long total) {
        return new TiendanubeBulkOperationResponse(
                id,
                TiendanubeBulkAction.PUBLISH,
                TiendanubeBulkOperationStatus.RUNNING,
                TiendanubeBulkSelectionType.FILTER,
                "test",
                total,
                total,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Instant.now(),
                null,
                Instant.now()
        );
    }
}