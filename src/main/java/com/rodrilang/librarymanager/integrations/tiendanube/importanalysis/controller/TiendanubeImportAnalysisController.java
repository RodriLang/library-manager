package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisBulkResolveRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisBulkResolveResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisCreateInventoryRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisResolveRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service.TiendanubeImportAnalysisManagementService;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service.TiendanubeImportAnalysisResolutionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Tiendanube - Análisis de importación",
        description = "Snapshot completo de Tiendanube, matching contra inventario y resolución de vínculos"
)
@RestController
@RequestMapping("/api/integrations/tiendanube/management/import-analysis-runs")
@RequiredArgsConstructor
public class TiendanubeImportAnalysisController {

    private final TiendanubeImportAnalysisManagementService managementService;
    private final TiendanubeImportAnalysisResolutionService resolutionService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeImportAnalysisRunResponse createRun() {
        return managementService.createRun();
    }

    @GetMapping
    public PageResponse<TiendanubeImportAnalysisRunResponse> getRuns(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return PageResponse.of(managementService.getRuns(pageable));
    }

    @GetMapping("/{runId}")
    public TiendanubeImportAnalysisRunResponse getRun(@PathVariable Long runId) {
        return managementService.getRun(runId);
    }

    @GetMapping("/{runId}/items")
    public PageResponse<TiendanubeImportAnalysisItemResponse> getItems(
            @PathVariable Long runId,
            @RequestParam(required = false) TiendanubeImportAnalysisItemStatus status,
            @RequestParam(required = false) TiendanubeImportAnalysisMatchType matchType,
            @RequestParam(required = false) Boolean missingIdentifier,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return PageResponse.of(managementService.getItems(
                runId,
                status,
                matchType,
                missingIdentifier,
                query,
                pageable
        ));
    }

    @GetMapping("/{runId}/items/{itemId}")
    public TiendanubeImportAnalysisItemResponse getItem(
            @PathVariable Long runId,
            @PathVariable Long itemId
    ) {
        return managementService.getItem(runId, itemId);
    }

    @PostMapping("/{runId}/items/{itemId}/resolve")
    public TiendanubeImportAnalysisItemResponse resolve(
            @PathVariable Long runId,
            @PathVariable Long itemId,
            @Valid @RequestBody TiendanubeImportAnalysisResolveRequest request
    ) {
        return resolutionService.resolve(runId, itemId, request);
    }

    @PostMapping("/{runId}/items/{itemId}/add-to-inventory-and-resolve")
    public TiendanubeImportAnalysisItemResponse createInventoryAndResolve(
            @PathVariable Long runId,
            @PathVariable Long itemId,
            @Valid @RequestBody TiendanubeImportAnalysisCreateInventoryRequest request
    ) {
        return resolutionService.createInventoryAndResolve(runId, itemId, request);
    }

    @PostMapping("/{runId}/items/{itemId}/ignore")
    public TiendanubeImportAnalysisItemResponse ignore(
            @PathVariable Long runId,
            @PathVariable Long itemId
    ) {
        return resolutionService.ignore(runId, itemId);
    }

    @PostMapping("/{runId}/link-ready")
    public TiendanubeImportAnalysisBulkResolveResponse linkReady(
            @PathVariable Long runId,
            @RequestBody(required = false) TiendanubeImportAnalysisBulkResolveRequest request
    ) {
        return resolutionService.linkReady(
                runId,
                request != null && request.shouldSyncStock()
        );
    }
}
