package com.rodrilang.librarymanager.purchasing.order.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreatePurchaseOrdersFromRequirementsRequest(

        @NotEmpty
        List<Long> requirementIds

) {
}