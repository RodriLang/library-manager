package com.rodrilang.librarymanager.inventory.category.controller;

import com.rodrilang.librarymanager.integrations.tiendanube.management.service.BookstoreCommercialCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory/commercial-categories")
public class CommercialCategoryController {
    private final BookstoreCommercialCategoryService categories;
    private final com.rodrilang.librarymanager.inventory.category.service.CommercialCategorySuggestionService suggestions;

    public record CategoryRequest(String name, Long parentId) {}

    @GetMapping("/counts")
    public java.util.List<BookstoreCommercialCategoryService.CategoryCount> counts() {
        return categories.counts();
    }

    @PostMapping("/suggestions/preview")
    public com.rodrilang.librarymanager.inventory.category.service.CommercialCategorySuggestionService.Preview previewSuggestions(
            @RequestBody com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkSelectionRequest selection) {
        return suggestions.preview(selection);
    }

    @PostMapping("/suggestions/apply")
    public com.rodrilang.librarymanager.inventory.category.service.CommercialCategorySuggestionService.Applied applySuggestions(
            @RequestBody com.rodrilang.librarymanager.inventory.category.service.CommercialCategorySuggestionService.ApplyRequest request) {
        return suggestions.apply(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookstoreCommercialCategoryService.Category create(@RequestBody CategoryRequest request) {
        return categories.createLocal(request == null ? null : request.name(),
                request == null ? null : request.parentId());
    }

    @PutMapping("/{id}")
    public BookstoreCommercialCategoryService.Category update(@PathVariable Long id,
            @RequestBody CategoryRequest request) {
        return categories.updateLocal(id, request == null ? null : request.name(),
                request == null ? null : request.parentId());
    }

    public record LinkedCategoryRequest(String name, Long parentId, boolean updateRemote) {}

    @PutMapping("/{id}/linked")
    public BookstoreCommercialCategoryService.Category updateLinked(
            @PathVariable Long id, @RequestBody LinkedCategoryRequest request) {
        return categories.updateLinked(id, request.name(), request.parentId(), request.updateRemote());
    }

    @PostMapping("/{id}/unlink")
    public BookstoreCommercialCategoryService.Category unlink(@PathVariable Long id) {
        return categories.unlinkLocal(id);
    }

    @DeleteMapping("/{id}/linked")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLinked(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean deleteRemote) {
        categories.deleteWithScope(id, deleteRemote);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categories.deleteLocal(id);
    }
}
