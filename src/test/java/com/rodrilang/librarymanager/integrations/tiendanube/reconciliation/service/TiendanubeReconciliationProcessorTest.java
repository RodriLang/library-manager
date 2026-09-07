package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeClaimedReconciliationRun;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationIssue;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRepairResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeReconciliationProcessorTest {

    @Mock private TiendanubeReconciliationDataService dataService;
    @Mock private TiendanubeClient client;
    @Mock private TiendanubeReconciliationComparisonService comparisonService;
    @Mock private TiendanubeReconciliationCompletionService completionService;
    @Mock private TiendanubeReconciliationRepairService repairService;

    @Test
    void autoRepairIsDisabledByDefault() {
        TiendanubeClaimedReconciliationRun run = run();
        List<TiendanubeReconciliationInventorySnapshot> snapshots = List.of(snapshot());
        List<TiendanubeReconciliationIssue> issues = List.of(issue());
        when(dataService.load(run)).thenReturn(snapshots);
        when(client.getProducts(7948456L)).thenReturn(List.of());
        when(comparisonService.compare(snapshots, List.of())).thenReturn(issues);

        processor(false).process(run);

        verify(completionService).complete(run, 1, 1, issues);
        verify(repairService, never()).repairAutomaticRun(2L, 3L);
    }

    @Test
    void autoRepairUsesCompletedRunWithoutChangingReconciliationResult() throws Exception {
        TiendanubeClaimedReconciliationRun run = run();
        List<TiendanubeReconciliationInventorySnapshot> snapshots = List.of(snapshot());
        List<TiendanubeReconciliationIssue> issues = List.of(issue());
        when(dataService.load(run)).thenReturn(snapshots);
        when(client.getProducts(7948456L)).thenReturn(List.of());
        when(comparisonService.compare(snapshots, List.of())).thenReturn(issues);
        when(repairService.repairAutomaticRun(2L, 3L)).thenReturn(
                new TiendanubeReconciliationRepairResponse(2L, 1, 1, 0, 0, 0)
        );

        processor(true).process(run);

        verify(completionService).complete(run, 1, 1, issues);
        verify(repairService).repairAutomaticRun(2L, 3L);
        verify(completionService, never()).fail(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    private TiendanubeReconciliationProcessor processor(boolean autoRepairEnabled) {
        TiendanubeReconciliationProcessor processor = new TiendanubeReconciliationProcessor(
                dataService,
                client,
                comparisonService,
                completionService,
                repairService
        );

        try {
            Field field = TiendanubeReconciliationProcessor.class.getDeclaredField("autoRepairEnabled");
            field.setAccessible(true);
            field.set(processor, autoRepairEnabled);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }

        return processor;
    }

    private TiendanubeClaimedReconciliationRun run() {
        return new TiendanubeClaimedReconciliationRun(2L, 3L, 30L, 7948456L, UUID.randomUUID());
    }

    private TiendanubeReconciliationInventorySnapshot snapshot() {
        return new TiendanubeReconciliationInventorySnapshot(
                11L,
                10L,
                358525030L,
                1568313931L,
                2,
                new BigDecimal("40000.00"),
                true
        );
    }

    private TiendanubeReconciliationIssue issue() {
        return new TiendanubeReconciliationIssue(
                11L,
                10L,
                358525030L,
                1568313931L,
                TiendanubeReconciliationIssueType.STOCK_MISMATCH,
                2,
                1,
                new BigDecimal("40000.00"),
                new BigDecimal("40000.00"),
                "Stock mismatch"
        );
    }
}
