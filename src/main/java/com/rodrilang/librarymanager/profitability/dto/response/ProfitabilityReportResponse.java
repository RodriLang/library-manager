package com.rodrilang.librarymanager.profitability.dto.response;

import java.time.Instant;

public record ProfitabilityReportResponse(

        Instant from,
        Instant to,
        Long saleCount,
        ProfitabilitySummaryResponse summary

) {
}
