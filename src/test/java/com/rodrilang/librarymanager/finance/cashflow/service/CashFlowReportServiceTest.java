package com.rodrilang.librarymanager.finance.cashflow.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.finance.cashflow.dto.response.CashFlowByMethodResponse;
import com.rodrilang.librarymanager.finance.cashflow.dto.response.CashFlowReportResponse;
import com.rodrilang.librarymanager.finance.cashflow.repository.CashFlowReportRepository;
import com.rodrilang.librarymanager.finance.cashflow.repository.CashFlowSnapshot;
import com.rodrilang.librarymanager.payment.model.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowReportServiceTest {

    @Mock
    private CashFlowReportRepository repository;

    @Mock
    private BookstoreContext bookstoreContext;

    @InjectMocks
    private CashFlowReportService service;

    @Test
    void calculatesNetCashFlowAndMergesMethods() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-30T23:59:59Z");

        when(bookstoreContext.getCurrentBookstoreId()).thenReturn(1L);
        when(repository.summarize(1L, from, to)).thenReturn(new CashFlowSnapshot(
                new BigDecimal("50000.00"),
                new BigDecimal("20000.00"),
                2,
                1,
                List.of(
                        new CashFlowSnapshot.MethodAmount("CASH", new BigDecimal("30000.00")),
                        new CashFlowSnapshot.MethodAmount("TRANSFER", new BigDecimal("20000.00"))
                ),
                List.of(
                        new CashFlowSnapshot.MethodAmount("TRANSFER", new BigDecimal("20000.00"))
                )
        ));

        CashFlowReportResponse result = service.get(from, to);

        assertEquals(new BigDecimal("30000.00"), result.netCashFlowAmount());
        assertEquals(2, result.byMethod().size());

        CashFlowByMethodResponse cash = result.byMethod().stream()
                .filter(row -> row.method() == PaymentMethod.CASH)
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("30000.00"), cash.netAmount());

        CashFlowByMethodResponse transfer = result.byMethod().stream()
                .filter(row -> row.method() == PaymentMethod.TRANSFER)
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("0.00"), transfer.netAmount());
    }
}
