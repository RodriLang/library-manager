package com.rodrilang.librarymanager.purchasing.requirement.repository.projection;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;

public interface PurchaseRequirementReasonProjection {

    Long getRequirementId();

    PurchaseRequirementSourceType getType();

    Long getQuantity();
}