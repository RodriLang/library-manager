package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.handler.TiendanubeJobHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiendanubeJobExecutorTest {

    @Test
    void executesRegisteredHandler() {
        AtomicBoolean executed = new AtomicBoolean(false);
        TiendanubeJobHandler handler = new TiendanubeJobHandler() {
            @Override
            public TiendanubeJobType type() {
                return TiendanubeJobType.SYNC_STOCK;
            }

            @Override
            public void execute(TiendanubeJobExecutionContext context) {
                executed.set(true);
            }
        };

        TiendanubeJobExecutor executor = new TiendanubeJobExecutor(List.of(handler));
        executor.execute(context(TiendanubeJobType.SYNC_STOCK));

        assertTrue(executed.get());
    }

    @Test
    void missingHandlerIsNonRetryable() {
        TiendanubeJobExecutor executor = new TiendanubeJobExecutor(List.of());

        TiendanubeJobExecutionException exception = assertThrows(
                TiendanubeJobExecutionException.class,
                () -> executor.execute(context(TiendanubeJobType.SYNC_PRICE))
        );

        assertEquals(TiendanubeJobFailureDisposition.FAIL, exception.getDisposition());
        assertEquals("HANDLER_NOT_FOUND", exception.getErrorType());
    }

    @Test
    void rejectsDuplicateHandlersForSameType() {
        TiendanubeJobHandler first = handler(TiendanubeJobType.PUBLISH);
        TiendanubeJobHandler second = handler(TiendanubeJobType.PUBLISH);

        assertThrows(IllegalStateException.class, () -> new TiendanubeJobExecutor(List.of(first, second)));
    }

    private TiendanubeJobHandler handler(TiendanubeJobType type) {
        return new TiendanubeJobHandler() {
            @Override
            public TiendanubeJobType type() {
                return type;
            }

            @Override
            public void execute(TiendanubeJobExecutionContext context) {
            }
        };
    }

    private TiendanubeJobExecutionContext context(TiendanubeJobType type) {
        return new TiendanubeJobExecutionContext(
                1L, 10L, 1, 7, 2L, 30L, 3L, 4L, type, TiendanubeJobSource.AUTOMATIC, UUID.randomUUID()
        );
    }
}
