package com.rodrilang.librarymanager.inventory.cost.dto.response;

import java.util.List;

public record BulkInventoryCostUpdateResponse(

        int updated,

        int failed,

        List<BulkInventoryCostFailureResponse> failures

) {
}
