package com.rodrilang.librarymanager.editorialprice.controller;

import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceConflictResolutionRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceDeactivationRequest;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceResolutionListResponse;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceControlService;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/editorial-prices/resolutions")
@RequiredArgsConstructor
public class EditorialPriceResolutionController {

    private final EditorialPriceResolutionService editorialPriceResolutionService;
    private final EditorialPriceControlService editorialPriceControlService;

    @GetMapping
    public Page<EditorialPriceResolutionListResponse> findAll(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, name = "q") String query,
            @PageableDefault(size = 30) Pageable pageable
    ) {
        return editorialPriceResolutionService.findAll(active, query, pageable);
    }

    @PatchMapping("/{resolutionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replace(
            @PathVariable Long resolutionId,
            @RequestBody EditorialPriceConflictResolutionRequest request,
            Authentication authentication
    ) {
        editorialPriceControlService.replaceResolution(resolutionId, request, authentication.getName());
    }

    @PatchMapping("/{resolutionId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @PathVariable Long resolutionId,
            @RequestBody(required = false) EditorialPriceDeactivationRequest request,
            Authentication authentication
    ) {
        editorialPriceControlService.deactivateResolution(
                resolutionId,
                request != null ? request.note() : null,
                authentication.getName()
        );
    }
}