package com.rodrilang.librarymanager.purchasing.order.dto.response;

import java.util.List;

public record CreatePurchaseOrdersFromRequirementsResponse(

        List<PreparedPurchaseOrderResponse> orders

) {
}