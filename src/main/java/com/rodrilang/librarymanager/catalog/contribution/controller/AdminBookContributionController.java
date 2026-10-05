package com.rodrilang.librarymanager.catalog.contribution.controller;

import com.rodrilang.librarymanager.catalog.contribution.dto.BookFieldProposalResponse;
import com.rodrilang.librarymanager.catalog.contribution.dto.ReviewBookFieldProposalRequest;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldProposalStatus;
import com.rodrilang.librarymanager.catalog.contribution.service.AdminBookContributionService;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/catalog/contributions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookContributionController {

    private final AdminBookContributionService contributionService;

    @GetMapping
    public ResponseEntity<PageResponse<BookFieldProposalResponse>> list(
            @RequestParam(required = false) BookFieldProposalStatus status,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) BookField field,
            @ParameterObject @PageableDefault(size = 30, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(
                contributionService.list(status, bookId, field, pageable)
        ));
    }

    @PutMapping("/{proposalId}")
    public ResponseEntity<BookFieldProposalResponse> review(
            @PathVariable Long proposalId,
            @Valid @RequestBody ReviewBookFieldProposalRequest request
    ) {
        return ResponseEntity.ok(contributionService.review(proposalId, request));
    }
}
