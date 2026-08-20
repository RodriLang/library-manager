package com.rodrilang.librarymanager.purchasing.provider.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.purchasing.provider.dto.ProviderCatalogFilter;
import com.rodrilang.librarymanager.purchasing.provider.dto.response.ProviderCatalogBookResponse;
import com.rodrilang.librarymanager.purchasing.provider.service.ProviderCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/price-list-providers")
@RequiredArgsConstructor
@Tag(name = "Catálogo de proveedores", description = "Consulta de libros comercializados por proveedores")
public class ProviderCatalogController {

    private final ProviderCatalogService service;

    @GetMapping("/{providerId}/catalog")
    public ResponseEntity<PageResponse<ProviderCatalogBookResponse>> findAll(
            @PathVariable Long providerId,
            @RequestParam(required = false) String query,
            @ParameterObject
            @PageableDefault(size = 20)
            Pageable pageable
    ) {

        ProviderCatalogFilter filter = new ProviderCatalogFilter(query);

        return ResponseEntity.ok(PageResponse.of(service.findAll(providerId, filter, pageable)));
    }
}