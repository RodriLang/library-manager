package com.rodrilang.librarymanager.integrations.tiendanube.management.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeBulkOperationRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeInventoryFilterRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeBulkOperationItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeBulkOperationResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeManagedInventoryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeManagementSummaryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminPublicationFilter;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminStockFilter;
import com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubeManagementService;
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
        name = "Tiendanube - Administración",
        description = "Administración centralizada del inventario y operaciones masivas de Tiendanube"
)
@RestController
@RequestMapping("/api/integrations/tiendanube/management")
@RequiredArgsConstructor
public class TiendanubeManagementController {

    private final TiendanubeManagementService managementService;

    @GetMapping("/summary")
    public TiendanubeManagementSummaryResponse getSummary() {
        return managementService.getSummary();
    }

    @GetMapping("/inventories")
    public PageResponse<TiendanubeManagedInventoryResponse> searchInventories(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") TiendanubeAdminPublicationFilter publication,
            @RequestParam(required = false) Long publisherId,
            @RequestParam(defaultValue = "ALL") TiendanubeAdminStockFilter stock,
            @RequestParam(required = false) Boolean priceSyncEnabled,
            @PageableDefault(size = 50, sort = "title") Pageable pageable
    ) {
        return PageResponse.of(
                managementService.searchInventories(
                        new TiendanubeInventoryFilterRequest(
                                q,
                                publication,
                                publisherId,
                                stock,
                                priceSyncEnabled
                        ),
                        pageable
                )
        );
    }

    @PostMapping("/bulk-operations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeBulkOperationResponse createBulkOperation(
            @Valid @RequestBody TiendanubeBulkOperationRequest request
    ) {
        return managementService.createBulkOperation(request);
    }

    @GetMapping("/bulk-operations")
    public PageResponse<TiendanubeBulkOperationResponse> getBulkOperations(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return PageResponse.of(
                managementService.getBulkOperations(pageable)
        );
    }

    @GetMapping("/bulk-operations/{operationId}")
    public TiendanubeBulkOperationResponse getBulkOperation(@PathVariable Long operationId) {
        return managementService.getBulkOperation(operationId);
    }

    @GetMapping("/bulk-operations/{operationId}/items")
    public PageResponse<TiendanubeBulkOperationItemResponse> getBulkOperationItems(
            @PathVariable Long operationId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return PageResponse.of(
                managementService.getBulkOperationItems(operationId, pageable)
        );
    }

    @PostMapping("/bulk-operations/{operationId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeBulkOperationResponse cancelBulkOperation(@PathVariable Long operationId) {
        return managementService.cancelBulkOperation(operationId);
    }

    @PostMapping("/bulk-operations/{operationId}/retry-failed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeBulkOperationResponse retryFailed(@PathVariable Long operationId) {
        return managementService.retryFailed(operationId);
    }
}
