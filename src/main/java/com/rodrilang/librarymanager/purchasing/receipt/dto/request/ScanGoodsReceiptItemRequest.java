package com.rodrilang.librarymanager.purchasing.receipt.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ScanGoodsReceiptItemRequest(@NotBlank String code) {}
