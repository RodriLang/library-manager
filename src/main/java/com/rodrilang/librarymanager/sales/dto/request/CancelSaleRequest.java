package com.rodrilang.librarymanager.sales.dto.request;

import jakarta.validation.constraints.Size;

public record CancelSaleRequest(

        @Size(max = 500)
        String reason

) {
}
