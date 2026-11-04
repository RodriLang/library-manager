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

    public record CategoryRequest(String name, Long parentId) {}

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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categories.deleteLocal(id);
    }
}
