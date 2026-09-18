package com.rodrilang.librarymanager.finance.cashflow.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.finance.cashflow.dto.response.CashFlowByMethodResponse;
import com.rodrilang.librarymanager.finance.cashflow.dto.response.CashFlowReportResponse;
import com.rodrilang.librarymanager.finance.cashflow.repository.CashFlowReportRepository;
import com.rodrilang.librarymanager.finance.cashflow.repository.CashFlowSnapshot;
import com.rodrilang.librarymanager.payment.model.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CashFlowReportService {

    private static final int MONEY_SCALE = 2;

    private final CashFlowReportRepository repository;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public CashFlowReportResponse get(Instant from, Instant to) {
        validateRange(from, to);

        CashFlowSnapshot snapshot = repository.summarize(
                bookstoreContext.getCurrentBookstoreId(),
                from,
                to
        );

        BigDecimal inflows = money(snapshot.salesReceiptsAmount());
        BigDecimal outflows = money(snapshot.supplierPaymentsAmount());

        return new CashFlowReportResponse(
                from,
                to,
                inflows,
                outflows,
                inflows.subtract(outflows).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                snapshot.salePaymentCount(),
                snapshot.purchasePaymentCount(),
                byMethod(snapshot)
        );
    }

    private List<CashFlowByMethodResponse> byMethod(CashFlowSnapshot snapshot) {
        Map<PaymentMethod, BigDecimal> inflows = amounts(snapshot.saleAmountsByMethod());
        Map<PaymentMethod, BigDecimal> outflows = amounts(snapshot.purchaseAmountsByMethod());

        return Stream.of(PaymentMethod.values())
                .map(method -> {
                    BigDecimal in = inflows.getOrDefault(method, BigDecimal.ZERO);
                    BigDecimal out = outflows.getOrDefault(method, BigDecimal.ZERO);
                    return new CashFlowByMethodResponse(
                            method,
                            money(in),
                            money(out),
                            money(in.subtract(out))
                    );
                })
                .filter(row -> row.inflowAmount().signum() != 0 || row.outflowAmount().signum() != 0)
                .toList();
    }

    private Map<PaymentMethod, BigDecimal> amounts(List<CashFlowSnapshot.MethodAmount> rows) {
        Map<PaymentMethod, BigDecimal> result = new EnumMap<>(PaymentMethod.class);
        rows.forEach(row -> result.put(
                PaymentMethod.valueOf(row.method()),
                money(row.amount())
        ));
        return result;
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new BusinessException("Deben informarse las fechas desde y hasta.");
        }
        if (from.isAfter(to)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
