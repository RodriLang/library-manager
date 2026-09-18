package com.rodrilang.librarymanager.sales.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateSaleRequest(

        @NotEmpty
        List<@NotNull @Valid CreateSaleItemRequest> items,

        @DecimalMin(value = "0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal discountAmount,

        @NotNull
        List<@NotNull @Valid CreateSalePaymentRequest> payments,

        @Size(max = 1000)
        String notes

) {
}
