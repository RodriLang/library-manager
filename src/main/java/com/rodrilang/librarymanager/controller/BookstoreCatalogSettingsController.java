package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.dto.request.UpdatePublisherExclusionRequest;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.dto.response.PublisherConfigurationDetailResponse;
import com.rodrilang.librarymanager.dto.response.PublisherConfigurationResponse;
import com.rodrilang.librarymanager.enums.PublisherCatalogSort;
import com.rodrilang.librarymanager.service.BookstoreCatalogSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookstore/catalog-settings")
@RequiredArgsConstructor
@Tag(name = "Configuración de catálogo", description = "Configuración del catálogo de la librería")
public class BookstoreCatalogSettingsController {

    private final BookstoreCatalogSettingsService catalogSettingsService;

    @GetMapping("/publishers")
    public ResponseEntity<PageResponse<PublisherConfigurationResponse>> getPublishers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean excluded,
            @RequestParam(defaultValue = "BOOK_COUNT_DESC") PublisherCatalogSort order,
            @ParameterObject
            @PageableDefault(size = 30)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(
                        catalogSettingsService.searchPublishers(
                                query,
                                excluded,
                                order,
                                pageable
                        )
                )
        );
    }

    @GetMapping("/publishers/{publisherId}")
    public ResponseEntity<PublisherConfigurationDetailResponse> getPublisher(
            @PathVariable Long publisherId
    ) {
        return ResponseEntity.ok(catalogSettingsService.getPublisher(publisherId));
    }

    @GetMapping("/publishers/{publisherId}/books")
    public ResponseEntity<PageResponse<BookSummaryResponse>> getPublisherBooks(
            @PathVariable Long publisherId,
            @RequestParam(required = false) String q,
            @ParameterObject
            @PageableDefault(size = 30, sort = "title")
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(
                        catalogSettingsService.getPublisherBooks(
                                publisherId,
                                q,
                                pageable
                        )
                )
        );
    }

    @PatchMapping("/publishers/{publisherId}")
    public ResponseEntity<Void> updatePublisherExclusion(
            @PathVariable Long publisherId,
            @Valid @RequestBody UpdatePublisherExclusionRequest request
    ) {
        catalogSettingsService.updatePublisherExclusion(
                publisherId,
                request.excluded()
        );

        return ResponseEntity.noContent().build();
    }
}