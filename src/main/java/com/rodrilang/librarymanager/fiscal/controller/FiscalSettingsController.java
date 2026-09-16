package com.rodrilang.librarymanager.fiscal.controller;

import com.rodrilang.librarymanager.fiscal.dto.request.UpdateFiscalSettingsRequest;
import com.rodrilang.librarymanager.fiscal.dto.response.FiscalSettingsResponse;
import com.rodrilang.librarymanager.fiscal.service.BookstoreFiscalSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fiscal/settings")
@RequiredArgsConstructor
@Tag(name = "Facturación - Configuración", description = "Datos fiscales y autorización ARCA de la librería")
public class FiscalSettingsController {

    private final BookstoreFiscalSettingsService service;

    @GetMapping
    public ResponseEntity<FiscalSettingsResponse> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    public ResponseEntity<FiscalSettingsResponse> update(
            @Valid @RequestBody UpdateFiscalSettingsRequest request
    ) {
        return ResponseEntity.ok(service.update(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<FiscalSettingsResponse> verifyAuthorization() {
        return ResponseEntity.ok(service.verifyAuthorization());
    }
}
