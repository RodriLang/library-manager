package com.rodrilang.librarymanager.sales.dto;

import com.rodrilang.librarymanager.sales.model.SaleOrigin;
import com.rodrilang.librarymanager.sales.model.SaleStatus;

import java.time.Instant;

public record SaleFilter(

        SaleStatus status,
        SaleOrigin origin,
        Instant from,
        Instant to

) {
}
