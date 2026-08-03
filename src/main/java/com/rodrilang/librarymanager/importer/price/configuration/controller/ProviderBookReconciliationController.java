package com.rodrilang.librarymanager.importer.price.configuration.controller;

import com.rodrilang.librarymanager.importer.price.configuration.dto.ProviderBookReconciliationPreview;
import com.rodrilang.librarymanager.importer.price.configuration.dto.ProviderBookReconciliationResult;
import com.rodrilang.librarymanager.importer.price.configuration.service.ProviderBookReconciliationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/provider-books")
@RequiredArgsConstructor
@Tag(
        name = "Provider Book Reconciliation",
        description = "Conciliación de libros entre los identificadores informados por los proveedores y el catálogo interno."
)
public class ProviderBookReconciliationController {

    private final ProviderBookReconciliationService reconciliationService;

    @GetMapping("/{providerBookId}/reconciliation-preview")
    public ResponseEntity<ProviderBookReconciliationPreview> preview(
            @PathVariable Long providerBookId,
            @RequestParam Long targetBookId
    ) {
        return ResponseEntity.ok(
                reconciliationService.preview(
                        providerBookId,
                        targetBookId
                )
        );
    }

    @PostMapping("/{providerBookId}/reconcile")
    public ResponseEntity<ProviderBookReconciliationResult> confirm(
            @PathVariable Long providerBookId,
            @RequestParam Long targetBookId
    ) {
        return ResponseEntity.ok(
                reconciliationService.confirm(
                        providerBookId,
                        targetBookId
                )
        );
    }
}