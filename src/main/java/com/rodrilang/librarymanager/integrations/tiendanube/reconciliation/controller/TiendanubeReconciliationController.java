package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRepairResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service.TiendanubeReconciliationManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Tiendanube - Reconciliación",
        description = "Detección y reparación segura de diferencias entre Anaquel y Tiendanube"
)
@RestController
@RequestMapping("/api/integrations/tiendanube/management/reconciliation-runs")
@RequiredArgsConstructor
public class TiendanubeReconciliationController {

    private final TiendanubeReconciliationManagementService reconciliationService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeReconciliationRunResponse createRun() {
        return reconciliationService.createManualRun();
    }

    @GetMapping
    public PageResponse<TiendanubeReconciliationRunResponse> getRuns(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return PageResponse.of(reconciliationService.getRuns(pageable));
    }

    @GetMapping("/{runId}")
    public TiendanubeReconciliationRunResponse getRun(@PathVariable Long runId) {
        return reconciliationService.getRun(runId);
    }

    @GetMapping("/{runId}/items")
    public PageResponse<TiendanubeReconciliationItemResponse> getItems(
            @PathVariable Long runId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return PageResponse.of(reconciliationService.getItems(runId, pageable));
    }

    @PostMapping("/{runId}/repair")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeReconciliationRepairResponse repairRun(@PathVariable Long runId) {
        return reconciliationService.repairRun(runId);
    }

    @PostMapping("/{runId}/items/{itemId}/repair")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeReconciliationItemResponse repairItem(
            @PathVariable Long runId,
            @PathVariable Long itemId
    ) {
        return reconciliationService.repairItem(runId, itemId);
    }
}
