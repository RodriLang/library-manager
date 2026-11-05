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
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private final com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubePublicationCorrectionService corrections;
    private final TiendanubeManagementService managementService;
    private final com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubeIgnoredPublicationService ignoredPublications;
    private final TiendanubePublicationContentService publicationContentService;

    private final com.rodrilang.librarymanager.integrations.tiendanube.management.service.BookstoreCommercialCategoryService commercialCategories;
    private final com.rodrilang.librarymanager.integrations.tiendanube.management.service.CommercialCategoryRemoteLinkService remoteCategoryLinks;

    public record LinkExistingCategoryRequest(Long remoteCategoryId) {}

    @GetMapping("/commercial-categories/link-suggestions")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.CommercialCategoryRemoteLinkService.LinkPreview categoryLinkSuggestions() {
        return remoteCategoryLinks.previewExactLinks();
    }

    public record ConfirmCategoryLinks(java.util.List<Long> categoryIds) {}

    @PostMapping("/commercial-categories/link-suggestions/apply")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.CommercialCategoryRemoteLinkService.BulkLinkResult applyCategoryLinkSuggestions(
            @RequestBody ConfirmCategoryLinks request) {
        return remoteCategoryLinks.applyExactLinks(request == null ? null : request.categoryIds());
    }

    @PostMapping("/commercial-categories/{categoryId}/link-existing")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.CommercialCategoryRemoteLinkService.LinkResult linkExistingCategory(
            @PathVariable Long categoryId, @RequestBody LinkExistingCategoryRequest request) {
        return remoteCategoryLinks.linkExisting(categoryId, request == null ? null : request.remoteCategoryId());
    }

    @PostMapping("/commercial-categories/{categoryId}/create-remote")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.CommercialCategoryRemoteLinkService.LinkResult createRemoteCategory(
            @PathVariable Long categoryId) {
        return remoteCategoryLinks.createRemote(categoryId);
    }


    @GetMapping("/commercial-categories/link-health")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.BookstoreCommercialCategoryService.LinkHealth verifyCategoryLinks() {
        return commercialCategories.verifyRemoteLinks();
    }

    @GetMapping("/commercial-categories")
    public java.util.List<com.rodrilang.librarymanager.integrations.tiendanube.management.service.BookstoreCommercialCategoryService.Category> commercialCategories() {
        return commercialCategories.list();
    }

    @PostMapping("/commercial-categories/import-tiendanube")
    public java.util.List<com.rodrilang.librarymanager.integrations.tiendanube.management.service.BookstoreCommercialCategoryService.Category> importCommercialCategories() {
        return commercialCategories.importRemote();
    }

    private final com.rodrilang.librarymanager.integrations.tiendanube.management.service.CommercialCategoryBulkAssignmentService categoryBulkAssignment;

    @GetMapping("/commercial-categories/{categoryId}/inventories")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.BookstoreCommercialCategoryService.CategoryInventoryPage categoryInventories(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "false") boolean includeSubcategories,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return commercialCategories.inventoriesInCategory(categoryId, includeSubcategories, q, page, size);
    }

    private final com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubeCategoryBulkPreviewService categoryBulkPreview;

    @PostMapping("/commercial-categories/preview-remote-bulk")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubeCategoryBulkPreviewService.Preview previewRemoteCategories(
            @Valid @RequestBody com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeBulkSelectionRequest selection) {
        return categoryBulkPreview.preview(selection);
    }

    @PostMapping("/commercial-categories/bulk-assign")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.CommercialCategoryBulkAssignmentService.Result bulkAssignCommercialCategories(
            @RequestBody com.rodrilang.librarymanager.integrations.tiendanube.management.service.CommercialCategoryBulkAssignmentService.Request request) {
        return categoryBulkAssignment.apply(request);
    }

    @GetMapping("/inventories/{inventoryId}/commercial-categories")
    public java.util.List<Long> inventoryCommercialCategories(@PathVariable Long inventoryId) {
        return commercialCategories.categoriesForInventory(inventoryId);
    }

    @PutMapping("/inventories/{inventoryId}/commercial-categories")
    public java.util.List<Long> updateInventoryCommercialCategories(@PathVariable Long inventoryId,
            @RequestBody CategorySelection selection) {
        return commercialCategories.assignToInventory(inventoryId, selection == null ? null : selection.categories());
    }

    private final com.rodrilang.librarymanager.integrations.tiendanube.management.service.InventoryCategorySyncService inventoryCategorySync;

    @GetMapping("/inventories/{inventoryId}/commercial-categories/compare-remote")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.InventoryCategorySyncService.Comparison compareInventoryCategories(@PathVariable Long inventoryId) {
        return inventoryCategorySync.compare(inventoryId);
    }

    public record ApplyCategorySyncRequest(java.util.List<Long> expectedLocal, java.util.List<Long> expectedRemote) {}

    @PostMapping("/inventories/{inventoryId}/commercial-categories/sync-remote")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.InventoryCategorySyncService.Comparison syncInventoryCategories(
            @PathVariable Long inventoryId, @RequestBody ApplyCategorySyncRequest request) {
        return inventoryCategorySync.apply(inventoryId, request == null ? null : request.expectedLocal(), request == null ? null : request.expectedRemote());
    }

    @GetMapping("/categories")
    public java.util.List<com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeCategoryResponse> categories() {
        return publicationContentService.categories();
    }

    public record CreateCategoryRequest(String name, Long parent) {}

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeCategoryResponse createCategory(
            @RequestBody CreateCategoryRequest request) {
        return publicationContentService.createCategory(request == null ? null : request.name(), request == null ? null : request.parent());
    }

    public record EditCategoryRequest(String name, Long parent) {}

    @PutMapping("/categories/{categoryId}")
    public com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeCategoryResponse editCategory(
            @PathVariable Long categoryId, @RequestBody EditCategoryRequest request) {
        return publicationContentService.editCategory(categoryId, request == null ? null : request.name(), request == null ? null : request.parent());
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long categoryId) {
        publicationContentService.deleteCategory(categoryId);
    }

    @GetMapping("/publications/{productId}/categories")
    public java.util.List<Long> publicationCategories(@PathVariable Long productId) {
        return publicationContentService.publicationCategories(productId);
    }

    public record CategorySelection(java.util.List<Long> categories) {}

    @PutMapping("/publications/{productId}/categories")
    public java.util.List<Long> updatePublicationCategories(@PathVariable Long productId,
                                                             @RequestBody CategorySelection selection) {
        return publicationContentService.updatePublicationCategories(productId, selection.categories());
    }

    @GetMapping("/publications")
    public PageResponse<com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationQualityResponse>
    searchPublications(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Boolean ignored,
                       @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(publicationContentService.searchPublications(q, ignored, pageable));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/publications/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUnlinkedPublication(@PathVariable Long productId,
            @RequestParam String confirm) {
        publicationContentService.deleteUnlinkedRemoteProduct(productId, confirm);
    }

    @GetMapping("/publications/ignored")
    public java.util.List<Long> ignoredPublications() {
        return ignoredPublications.list();
    }

    @PutMapping("/publications/{productId}/ignored")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ignorePublication(@PathVariable long productId) {
        ignoredPublications.ignore(productId);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/publications/{productId}/ignored")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restorePublication(@PathVariable long productId) {
        ignoredPublications.restore(productId);
    }

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

    @PostMapping("/publication-corrections/preview")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubePublicationCorrectionService.Job previewCorrections(
        @RequestBody com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubePublicationCorrectionService.Request request) {
        return corrections.preview(request);
    }
    @GetMapping("/publication-corrections/{id}")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubePublicationCorrectionService.Job correctionJob(@PathVariable Long id) {
        return corrections.get(id);
    }
    @PostMapping("/publication-corrections/{id}/execute")
    public com.rodrilang.librarymanager.integrations.tiendanube.management.service.TiendanubePublicationCorrectionService.Job executeCorrections(@PathVariable Long id) {
        return corrections.execute(id);
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
            @RequestParam(required = false) String categorySyncStatus,
            @PageableDefault(size = 50, sort = "title") Pageable pageable
    ) {
        return PageResponse.of(
                managementService.searchInventories(
                        new TiendanubeInventoryFilterRequest(
                                q,
                                publication,
                                publisherId,
                                stock,
                                priceSyncEnabled,
                                categorySyncStatus
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
            @RequestParam(defaultValue = "false") boolean failuresOnly,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return PageResponse.of(
                managementService.getBulkOperationItems(operationId, pageable, failuresOnly)
        );
    }

    @PostMapping("/bulk-operations/{operationId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeBulkOperationResponse cancelBulkOperation(@PathVariable Long operationId) {
        return managementService.cancelBulkOperation(operationId);
    }

    public record RetrySelectedCategoriesRequest(java.util.List<Long> inventoryIds) {}

    @PostMapping("/bulk-operations/{operationId}/retry-selected-categories")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeBulkOperationResponse retrySelectedCategories(
            @PathVariable Long operationId,
            @RequestBody RetrySelectedCategoriesRequest request) {
        return managementService.retrySelectedCategoryFailures(operationId,
                request == null ? null : request.inventoryIds());
    }

    @PostMapping("/bulk-operations/{operationId}/retry-failed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TiendanubeBulkOperationResponse retryFailed(@PathVariable Long operationId) {
        return managementService.retryFailed(operationId);
    }
}
