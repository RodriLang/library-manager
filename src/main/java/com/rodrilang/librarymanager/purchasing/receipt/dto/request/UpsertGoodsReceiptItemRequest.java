package com.rodrilang.librarymanager.purchasing.receipt.dto.request;

import com.rodrilang.librarymanager.enums.BookCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertGoodsReceiptItemRequest(
        @NotNull Long bookId,
        BookCondition condition,
        @Min(0) Integer documentQuantity,
        @NotNull @Min(0) Integer receivedQuantity,
        @Size(max = 500) String notes
) {}
