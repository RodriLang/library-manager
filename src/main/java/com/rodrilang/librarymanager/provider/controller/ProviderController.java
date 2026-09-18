package com.rodrilang.librarymanager.provider.controller;

import com.rodrilang.librarymanager.provider.dto.request.CreateProviderRequest;
import com.rodrilang.librarymanager.provider.dto.request.UpdateProviderRequest;
import com.rodrilang.librarymanager.provider.dto.response.ProviderResponse;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.service.ProviderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Proveedores",
        description = "Proveedores comerciales y fuentes internas de Anaquel"
)
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @PostMapping
    public ResponseEntity<ProviderResponse> create(@Valid @RequestBody CreateProviderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(providerService.create(request));
    }

    @PutMapping("/{providerId}")
    public ResponseEntity<ProviderResponse> update(
            @PathVariable Long providerId,
            @Valid @RequestBody UpdateProviderRequest request
    ) {
        return ResponseEntity.ok(providerService.update(providerId, request));
    }

    @GetMapping
    public ResponseEntity<List<ProviderResponse>> findAll(
            @RequestParam(defaultValue = "COMMERCIAL") ProviderType type
    ) {
        return ResponseEntity.ok(providerService.findAllActive(type));
    }

    @GetMapping("/{providerId}")
    public ResponseEntity<ProviderResponse> findById(@PathVariable Long providerId) {
        return ResponseEntity.ok(providerService.findById(providerId));
    }
}
