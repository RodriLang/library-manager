package com.rodrilang.librarymanager.editorialprice.controller;

import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceConfirmationRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceConflictResolutionRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceDeactivationRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.ManualEditorialPriceRequest;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceBookDetailResponse;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceControlService;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/editorial-prices")
@RequiredArgsConstructor
public class EditorialPriceController {

    private final EditorialPriceControlService editorialPriceControlService;
    private final EditorialPriceQueryService editorialPriceQueryService;

    @PostMapping("/books/{bookId}/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public void createManualPrice(
            @PathVariable Long bookId,
            @RequestBody ManualEditorialPriceRequest request,
            Authentication authentication
    ) {
        editorialPriceControlService.createManualPrice(
                bookId,
                request,
                authentication.getName()
        );
    }

    @PostMapping("/{editorialPriceId}/confirmations")
    @ResponseStatus(HttpStatus.CREATED)
    public void confirmPrice(
            @PathVariable Long editorialPriceId,
            @RequestBody EditorialPriceConfirmationRequest request,
            Authentication authentication
    ) {
        editorialPriceControlService.confirmPrice(
                editorialPriceId,
                request,
                authentication.getName()
        );
    }

    @PostMapping("/books/{bookId}/conflicts/{validFrom}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolveConflict(
            @PathVariable Long bookId,

            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate validFrom,

            @RequestBody
            EditorialPriceConflictResolutionRequest request,

            Authentication authentication
    ) {
        editorialPriceControlService.resolveConflict(
                bookId,
                validFrom,
                request,
                authentication.getName()
        );
    }

    @GetMapping("/books/{bookId}")
    public EditorialPriceBookDetailResponse getBookDetail(@PathVariable Long bookId) {
        return editorialPriceQueryService.getBookDetail(bookId);
    }

    @PatchMapping("/{editorialPriceId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivatePrice(
            @PathVariable Long editorialPriceId,
            @RequestBody(required = false) EditorialPriceDeactivationRequest request,
            Authentication authentication
    ) {
        editorialPriceControlService.deactivatePrice(
                editorialPriceId,
                request != null ? request.note() : null,
                authentication.getName()
        );
    }
}