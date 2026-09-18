package com.rodrilang.librarymanager.purchasing.controller;

import com.rodrilang.librarymanager.purchasing.dto.request.UpsertProviderBookTermRequest;
import com.rodrilang.librarymanager.purchasing.dto.response.ProviderBookTermResponse;
import com.rodrilang.librarymanager.purchasing.service.ProviderBookTermService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers/{providerId}/commercial-terms")
@RequiredArgsConstructor
@Tag(
        name = "Condiciones comerciales de proveedores",
        description = "Descuentos habituales por libro para la librería actual"
)
public class ProviderCommercialTermController {

    private final ProviderBookTermService termService;

    @GetMapping
    public ResponseEntity<List<ProviderBookTermResponse>> findAll(@PathVariable Long providerId) {
        return ResponseEntity.ok(termService.findByProvider(providerId));
    }

    @PutMapping
    public ResponseEntity<ProviderBookTermResponse> upsert(
            @PathVariable Long providerId,
            @Valid @RequestBody UpsertProviderBookTermRequest request
    ) {
        return ResponseEntity.ok(termService.upsert(providerId, request));
    }
}
