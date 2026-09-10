package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.model.Bookstore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeJobConnectionGuardTest {

    @Mock
    private TiendanubeStoreRepository storeRepository;

    @Test
    void acceptsCurrentActiveConnection() {
        TiendanubeStore store = store(30L, 3L, true, true, 2L);
        when(storeRepository.findById(30L)).thenReturn(Optional.of(store));

        new TiendanubeJobConnectionGuard(storeRepository).validate(context(30L, 3L, 2L));
    }

    @Test
    void blocksJobWhenRemoteStoreChanged() {
        TiendanubeStore store = store(30L, 99L, true, true, 2L);
        when(storeRepository.findById(30L)).thenReturn(Optional.of(store));

        TiendanubeJobExecutionException exception = assertThrows(
                TiendanubeJobExecutionException.class,
                () -> new TiendanubeJobConnectionGuard(storeRepository).validate(context(30L, 3L, 2L))
        );

        assertEquals(TiendanubeJobFailureDisposition.BLOCK, exception.getDisposition());
        assertEquals("STORE_CHANGED", exception.getErrorType());
    }

    private TiendanubeStore store(Long id, Long storeId, boolean active, boolean tokenValid, Long bookstoreId) {
        Bookstore bookstore = mock(Bookstore.class);
        org.mockito.Mockito.lenient().when(bookstore.getId()).thenReturn(bookstoreId);

        return TiendanubeStore.builder()
                .id(id)
                .bookstore(bookstore)
                .storeId(storeId)
                .active(active)
                .tokenValid(tokenValid)
                .build();
    }

    private TiendanubeJobExecutionContext context(Long internalStoreId, Long storeId, Long bookstoreId) {
        return new TiendanubeJobExecutionContext(
                1L, 10L, 1, 7, bookstoreId, internalStoreId, storeId, 4L,
                TiendanubeJobType.SYNC_STOCK, TiendanubeJobSource.AUTOMATIC, UUID.randomUUID()
        );
    }
}
