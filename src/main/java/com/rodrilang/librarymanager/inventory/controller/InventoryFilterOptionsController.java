package com.rodrilang.librarymanager.inventory.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.dto.response.SelectableEntityResponse;
import com.rodrilang.librarymanager.inventory.dto.filter.InventoryFilterOptionsRequest;
import com.rodrilang.librarymanager.inventory.dto.filter.InventoryFilterOptionsResponse;
import com.rodrilang.librarymanager.service.InventoryFilterOptionsService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/filter-options")
@RequiredArgsConstructor
public class InventoryFilterOptionsController {

    private final InventoryFilterOptionsService service;

    @GetMapping("/authors")
    public ResponseEntity<PageResponse<SelectableEntityResponse>> searchAuthors(
            @RequestParam String q,
            @ParameterObject
            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(
                        service.searchAuthors(
                                q,
                                pageable
                        )
                )
        );
    }

    @GetMapping("/publishers")
    public ResponseEntity<PageResponse<SelectableEntityResponse>> searchPublishers(
            @RequestParam String q,
            @ParameterObject
            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(
                        service.searchPublishers(
                                q,
                                pageable
                        )
                )
        );
    }

    @PostMapping("/resolve")
    public ResponseEntity<InventoryFilterOptionsResponse> resolve(
            @RequestBody InventoryFilterOptionsRequest request
    ) {
        return ResponseEntity.ok(
                service.resolve(request)
        );
    }
}