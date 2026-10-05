package com.rodrilang.librarymanager.purchasing.receipt.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateGoodsReceiptRequest(
        Long providerId,
        @Size(max = 30) String documentType,
        @Size(max = 100) String documentNumber,
        @Size(max = 1000) String notes
) {}
