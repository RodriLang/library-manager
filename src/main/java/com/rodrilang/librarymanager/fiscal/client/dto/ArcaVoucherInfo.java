package com.rodrilang.librarymanager.fiscal.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ArcaVoucherInfo(

        long voucherNumber,
        LocalDate issueDate,
        BigDecimal total,
        String cae,
        LocalDate caeExpirationDate,
        String result,
        String observations

) {
}
