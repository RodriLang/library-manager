package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.metadata.enrichment.BookMetadataEnrichmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Metadatos de libros", description = "Búsqueda y enriquecimiento de metadatos de los libros")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books/metadata")
public class BookMetadataEnrichmentController {

    private final BookMetadataEnrichmentService enrichmentService;

    @PostMapping("/enrich-pending")
    public ResponseEntity<Integer> enrichPending(
            @RequestParam(defaultValue = "50") int limit
    ) {
        int updated = enrichmentService.enrichPendingBooks(limit);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/enrich/{bookId}")
    public ResponseEntity<Boolean> enrichOne(@PathVariable Long bookId) {
        boolean updated = enrichmentService.enrichBook(bookId);
        return ResponseEntity.ok(updated);
    }
}