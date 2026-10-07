package com.rodrilang.librarymanager.catalog.contribution.controller;

import com.rodrilang.librarymanager.catalog.contribution.dto.BookContributionRequest;
import com.rodrilang.librarymanager.catalog.contribution.dto.BookContributionResponse;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.service.BookContributionService;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books/{bookId}/contributions")
@RequiredArgsConstructor
public class BookContributionController {

    private final BookContributionService contributionService;

    @PostMapping
    public ResponseEntity<BookContributionResponse> contribute(
            @PathVariable Long bookId,
            @Valid @RequestBody BookContributionRequest request
    ) {
        return ResponseEntity.ok(contributionService.contribute(bookId, request));
    }

    @DeleteMapping("/overrides/{field}")
    public ResponseEntity<BookDetailResponse> resetOverride(
            @PathVariable Long bookId,
            @PathVariable BookField field
    ) {
        return ResponseEntity.ok(contributionService.resetOverride(bookId, field));
    }
}
