package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkOperationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TiendanubeBulkOperationResponseTest {

    @Test
    void runningWhileThereAreActiveItems() {
        assertEquals(
                TiendanubeBulkOperationStatus.RUNNING,
                TiendanubeBulkOperationResponse.resolveStatus(null, 2, 1, 0, 0, 0, 0, 0, 0, 0)
        );
    }

    @Test
    void cancelRequestedWhileQueuedWorkStillExists() {
        assertEquals(
                TiendanubeBulkOperationStatus.CANCEL_REQUESTED,
                TiendanubeBulkOperationResponse.resolveStatus(
                        Instant.now(), 0, 0, 1, 0, 3, 0, 0, 4, 0
                )
        );
    }

    @Test
    void completedWhenEveryItemSucceeded() {
        assertEquals(
                TiendanubeBulkOperationStatus.COMPLETED,
                TiendanubeBulkOperationResponse.resolveStatus(null, 0, 0, 0, 0, 10, 0, 0, 0, 0)
        );
    }

    @Test
    void completedWithIssuesWhenSomeItemsFailedOrWereSkipped() {
        assertEquals(
                TiendanubeBulkOperationStatus.COMPLETED_WITH_ISSUES,
                TiendanubeBulkOperationResponse.resolveStatus(null, 0, 0, 0, 0, 8, 1, 0, 0, 1)
        );
    }

    @Test
    void cancelledWhenNothingWasExecutedAfterCancellation() {
        assertEquals(
                TiendanubeBulkOperationStatus.CANCELLED,
                TiendanubeBulkOperationResponse.resolveStatus(Instant.now(), 0, 0, 0, 0, 0, 0, 0, 5, 0)
        );
    }
}
