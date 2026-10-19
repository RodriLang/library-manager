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
import com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubePublicationContentService;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubePublicationContentRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationContentResponse;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final TiendanubePublicationContentService publicationContentService;

    @GetMapping("/publications/{productId}")
    public TiendanubePublicationContentResponse getRemotePublication(@PathVariable Long productId) {
        return publicationContentService.getRemote(productId);
    }

    @PutMapping("/publications/{productId}/content")
    public TiendanubePublicationContentResponse updateRemotePublication(
            @PathVariable Long productId, @Valid @RequestBody TiendanubePublicationContentRequest request) {
        return publicationContentService.updateRemote(productId, request);
    }

    @PostMapping("/publications/{productId}/cover")
    public TiendanubePublicationContentResponse addRemoteCover(
            @PathVariable Long productId,
            @Valid @RequestBody com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubePublicationCoverRequest request) {
        return publicationContentService.addRemoteCover(productId, request.url(), request.useAnaquelCover());
    }

    @PostMapping("/publications/{productId}/complete-identifiers")
    public TiendanubePublicationContentResponse completeRemoteIdentifiers(@PathVariable Long productId) {
        return publicationContentService.completeRemoteIdentifiers(productId);
    }

    @GetMapping("/inventories/{inventoryId}/publication-content")
    public TiendanubePublicationContentResponse getPublicationContent(@PathVariable Long inventoryId) {
        return publicationContentService.get(inventoryId);
    }

    @PutMapping("/inventories/{inventoryId}/publication-content")
    public TiendanubePublicationContentResponse updatePublicationContent(
            @PathVariable Long inventoryId, @Valid @RequestBody TiendanubePublicationContentRequest request) {
        return publicationContentService.update(inventoryId, request);
    }

    @PostMapping("/inventories/{inventoryId}/complete-identifiers")
    public TiendanubePublicationContentResponse completeIdentifiers(@PathVariable Long inventoryId) {
        return publicationContentService.completeIdentifiers(inventoryId);
    }

    @GetMapping("/publication-quality")
    public java.util.List<com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationQualityResponse> publicationQuality() {
        return publicationContentService.auditPublicationQuality();
    }

    @GetMapping("/publication-duplicates")
    public java.util.List<com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeDuplicatePairResponse> publicationDuplicates() {
        return publicationContentService.findGlobalDuplicates();
    }

    @PostMapping("/publication-duplicates/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissDuplicate(@RequestBody java.util.Map<String, Long> request) {
        publicationContentService.dismissDuplicate(request.get("productIdA"), request.get("productIdB"));
    }

    private final com.rodrilang.librarymanager.bookstore.BookstoreContext bookstoreContext;
    private final com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubePublicationTemplateService templateService;

    @GetMapping("/publication-template")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationTemplateResponse getTemplate() {
        return templateService.get(bookstoreContext.getCurrentBookstoreId());
    }

    @PutMapping("/publication-template")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationTemplateResponse updateTemplate(
            @Valid @RequestBody com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubePublicationTemplateRequest request) {
        return templateService.save(bookstoreContext.getCurrentBookstoreId(), request);
    }

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
