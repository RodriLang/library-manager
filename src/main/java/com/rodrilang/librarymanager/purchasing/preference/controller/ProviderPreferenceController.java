package com.rodrilang.librarymanager.purchasing.preference.controller;

import com.rodrilang.librarymanager.purchasing.preference.dto.request.SetPreferredProviderRequest;
import com.rodrilang.librarymanager.purchasing.preference.dto.response.PreferredProviderResponse;
import com.rodrilang.librarymanager.purchasing.preference.service.ProviderPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/book-provider-preferences")
@RequiredArgsConstructor
public class ProviderPreferenceController {

    private final ProviderPreferenceService service;

    @GetMapping("/books/{bookId}")
    public ResponseEntity<PreferredProviderResponse> find(@PathVariable Long bookId) {
        return ResponseEntity.ok(service.findForCurrentBookstore(bookId));
    }

    @PutMapping("/books/{bookId}")
    public ResponseEntity<PreferredProviderResponse> set(
            @PathVariable Long bookId,
            @Valid @RequestBody SetPreferredProviderRequest request
    ) {
        return ResponseEntity.ok(service.setManual(bookId, request.providerId()));
    }

    @DeleteMapping("/books/{bookId}")
    public ResponseEntity<Void> clear(@PathVariable Long bookId) {
        service.clearManual(bookId);
        return ResponseEntity.noContent().build();
    }
}
