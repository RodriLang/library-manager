package com.rodrilang.librarymanager.purchasing.controller;

import com.rodrilang.librarymanager.purchasing.dto.request.*;
import com.rodrilang.librarymanager.purchasing.dto.response.*;
import com.rodrilang.librarymanager.purchasing.service.BookSupplierTermService;
import com.rodrilang.librarymanager.purchasing.service.SupplierService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Tag(name = "Proveedores", description = "Proveedores y condiciones comerciales por libro")
public class SupplierController {
    private final SupplierService supplierService;
    private final BookSupplierTermService termService;

    @GetMapping
    public ResponseEntity<List<SupplierResponse>> findAll() {
        return ResponseEntity.ok(supplierService.findAll());
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.ok(supplierService.create(request));
    }

    @PutMapping("/{supplierId}")
    public ResponseEntity<SupplierResponse> update(
            @PathVariable Long supplierId,
            @Valid @RequestBody UpdateSupplierRequest request
    ) {
        return ResponseEntity.ok(supplierService.update(supplierId, request));
    }

    @GetMapping("/{supplierId}/terms")
    public ResponseEntity<List<BookSupplierTermResponse>> findTerms(@PathVariable Long supplierId) {
        return ResponseEntity.ok(termService.findBySupplier(supplierId));
    }

    @PutMapping("/{supplierId}/terms")
    public ResponseEntity<BookSupplierTermResponse> upsertTerm(
            @PathVariable Long supplierId,
            @Valid @RequestBody UpsertBookSupplierTermRequest request
    ) {
        return ResponseEntity.ok(termService.upsert(supplierId, request));
    }
}
