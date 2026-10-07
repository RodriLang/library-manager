package com.rodrilang.librarymanager.provider.admin.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.provider.admin.dto.ReviewProviderRequest;
import com.rodrilang.librarymanager.provider.admin.service.AdminProviderReviewService;
import com.rodrilang.librarymanager.provider.dto.response.ProviderResponse;
import com.rodrilang.librarymanager.provider.model.ProviderVerificationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/providers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProviderReviewController {

    private final AdminProviderReviewService service;

    @GetMapping
    public ResponseEntity<PageResponse<ProviderResponse>> list(
            @RequestParam(defaultValue = "PENDING_REVIEW") ProviderVerificationStatus status,
            @ParameterObject @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.list(status, pageable));
    }

    @PutMapping("/{providerId}/review")
    public ResponseEntity<ProviderResponse> review(
            @PathVariable Long providerId,
            @Valid @RequestBody ReviewProviderRequest request
    ) {
        return ResponseEntity.ok(service.review(providerId, request));
    }
}
