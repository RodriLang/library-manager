package com.rodrilang.librarymanager.cover.controller;

import com.rodrilang.librarymanager.cover.dto.BookCoverProcessingResult;
import com.rodrilang.librarymanager.cover.service.BookCoverProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/book-covers/processing")
@RequiredArgsConstructor
@Tag(
        name = "Book Cover Processing",
        description = "Procesamiento asíncrono de portadas pendientes"
)
public class BookCoverProcessingController {

    private final BookCoverProcessingService processingService;

    @PostMapping("/run")
    @Operation(
            summary = "Procesar siguiente lote",
            description = "Reclama y procesa manualmente el siguiente lote de portadas candidatas."
    )
    public ResponseEntity<BookCoverProcessingResult> run() {
        return ResponseEntity.ok(
                processingService.processNextBatch()
        );
    }
}