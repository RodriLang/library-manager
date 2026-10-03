package com.rodrilang.librarymanager.catalog.candidate.controller;

import com.rodrilang.librarymanager.catalog.candidate.dto.request.CreateCatalogCandidateBookRequest;
import com.rodrilang.librarymanager.catalog.candidate.dto.response.CatalogCandidateResponse;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;
import com.rodrilang.librarymanager.catalog.candidate.service.CatalogCandidateService;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog-candidates")
@RequiredArgsConstructor
@Tag(name = "Candidatos de catálogo", description = "Libros escaneados que todavía no pudieron resolverse en el catálogo")
public class CatalogCandidateController {

    private final CatalogCandidateService service;

    @GetMapping
    public ResponseEntity<PageResponse<CatalogCandidateResponse>> findAll(
            @RequestParam(required = false) CatalogCandidateStatus status,
            @ParameterObject
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(service.findAll(status, pageable)));
    }

    @GetMapping("/{candidateId}")
    public ResponseEntity<CatalogCandidateResponse> findById(@PathVariable Long candidateId) {
        return ResponseEntity.ok(service.findById(candidateId));
    }

    @PostMapping("/{candidateId}/resolve/book/{bookId}")
    public ResponseEntity<CatalogCandidateResponse> resolveWithBook(
            @PathVariable Long candidateId,
            @PathVariable Long bookId
    ) {
        return ResponseEntity.ok(service.resolveWithBook(candidateId, bookId));
    }

    @PostMapping("/{candidateId}/resolve")
    public ResponseEntity<CatalogCandidateResponse> createBookAndResolve(
            @PathVariable Long candidateId,
            @Valid @RequestBody CreateCatalogCandidateBookRequest request
    ) {
        return ResponseEntity.ok(service.createBookAndResolve(candidateId, request));
    }
}
