package com.rodrilang.librarymanager.purchasing.requirement.service;

import com.rodrilang.librarymanager.purchasing.requirement.dto.PurchaseRequirementFilter;
import com.rodrilang.librarymanager.purchasing.requirement.dto.internal.AddPurchaseRequirementCommand;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.AddPurchaseRequirementResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.BookPurchaseRequirementStatusResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseRequirementService {

    AddPurchaseRequirementResponse addManualRequirement(AddPurchaseRequirementCommand command);

    PurchaseRequirementResponse addRequirement(AddPurchaseRequirementCommand command);

    AddPurchaseRequirementResponse undoSource(Long requirementId, Long sourceId);

    PurchaseRequirementResponse reactivate(Long requirementId);

    PurchaseRequirementResponse adjust(Long requirementId, Integer quantity);

    PurchaseRequirementResponse assignProvider(Long requirementId, Long providerId);

    void cancel(Long requirementId);

    PurchaseRequirementResponse findById(Long requirementId);

    BookPurchaseRequirementStatusResponse findBookStatus(Long bookId);

    Page<PurchaseRequirementSummaryResponse> findAll(PurchaseRequirementFilter filter, Pageable pageable);
}