package com.rodrilang.librarymanager.purchasing.requirement.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.PurchaseRequirementFilter;
import com.rodrilang.librarymanager.purchasing.requirement.dto.internal.AddPurchaseRequirementCommand;
import com.rodrilang.librarymanager.purchasing.requirement.dto.request.AddPurchaseRequirementRequest;
import com.rodrilang.librarymanager.purchasing.requirement.dto.request.AdjustPurchaseRequirementRequest;
import com.rodrilang.librarymanager.purchasing.requirement.dto.request.AssignPurchaseRequirementProviderRequest;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.AddPurchaseRequirementResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.BookPurchaseRequirementStatusResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementSummaryResponse;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;
import com.rodrilang.librarymanager.purchasing.requirement.service.PurchaseRequirementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-requirements")
@RequiredArgsConstructor
@Tag(name = "Necesidades de compra", description = "Gestión de libros pendientes de compra o reposición")
public class PurchaseRequirementController {

    private final PurchaseRequirementService service;

    @PostMapping
    public ResponseEntity<AddPurchaseRequirementResponse> add(
            @Valid
            @RequestBody AddPurchaseRequirementRequest request
    ) {

        AddPurchaseRequirementResponse response =
                service.addManualRequirement(
                        new AddPurchaseRequirementCommand(
                                request.bookId(),
                                request.quantity(),
                                request.source(),
                                null,
                                request.providerId()
                        )
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{requirementId}/sources/{sourceId}/undo")
    public ResponseEntity<AddPurchaseRequirementResponse> undo(
            @PathVariable Long requirementId,
            @PathVariable Long sourceId
    ) {

        return ResponseEntity.ok(
                service.undoSource(
                        requirementId,
                        sourceId
                )
        );
    }

    @PostMapping("/{requirementId}/reactivate")
    public ResponseEntity<PurchaseRequirementResponse> reactivate(
            @PathVariable Long requirementId
    ) {

        return ResponseEntity.ok(
                service.reactivate(requirementId)
        );
    }

    @PatchMapping("/{requirementId}/quantity")
    public ResponseEntity<PurchaseRequirementResponse> adjust(
            @PathVariable Long requirementId,
            @Valid
            @RequestBody AdjustPurchaseRequirementRequest request
    ) {

        return ResponseEntity.ok(
                service.adjust(
                        requirementId,
                        request.quantity()
                )
        );
    }

    @PatchMapping("/{requirementId}/provider")
    public ResponseEntity<PurchaseRequirementResponse> assignProvider(
            @PathVariable Long requirementId,
            @RequestBody AssignPurchaseRequirementProviderRequest request
    ) {

        return ResponseEntity.ok(
                service.assignProvider(
                        requirementId,
                        request.providerId()
                )
        );
    }

    @GetMapping("/{requirementId}")
    public ResponseEntity<PurchaseRequirementResponse> findById(
            @PathVariable Long requirementId
    ) {

        return ResponseEntity.ok(
                service.findById(requirementId)
        );
    }

    @GetMapping("/books/{bookId}/status")
    public ResponseEntity<BookPurchaseRequirementStatusResponse> findBookStatus(
            @PathVariable Long bookId
    ) {
        return ResponseEntity.ok(service.findBookStatus(bookId));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PurchaseRequirementSummaryResponse>> findAll(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false, defaultValue = "PENDING") PurchaseRequirementStatus status,
            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        PurchaseRequirementFilter filter =
                new PurchaseRequirementFilter(
                        query,
                        providerId,
                        status
                );

        return ResponseEntity.ok(
                PageResponse.of(
                        service.findAll(
                                filter,
                                pageable
                        )
                )
        );
    }

    @DeleteMapping("/{requirementId}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long requirementId
    ) {

        service.cancel(requirementId);

        return ResponseEntity.noContent().build();
    }
}