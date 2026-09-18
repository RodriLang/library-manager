package com.rodrilang.librarymanager.purchasing.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPurchasePaymentRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {
}
