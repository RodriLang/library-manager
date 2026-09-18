package com.rodrilang.librarymanager.finance.cashflow.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CashFlowReportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CashFlowSnapshot summarize(
            Long bookstoreId,
            Instant from,
            Instant to
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue(
                        "from",
                        from.atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .addValue(
                        "to",
                        to.atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE
                );

        Totals saleTotals = totals(SALE_TOTALS_SQL, params);
        Totals purchaseTotals = totals(PURCHASE_TOTALS_SQL, params);

        return new CashFlowSnapshot(
                saleTotals.amount(),
                purchaseTotals.amount(),
                saleTotals.count(),
                purchaseTotals.count(),
                byMethod(SALE_BY_METHOD_SQL, params),
                byMethod(PURCHASE_BY_METHOD_SQL, params)
        );
    }

    private Totals totals(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Totals(
                rs.getBigDecimal("amount"),
                rs.getLong("payment_count")
        ));
    }

    private List<CashFlowSnapshot.MethodAmount> byMethod(
            String sql,
            MapSqlParameterSource params
    ) {
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new CashFlowSnapshot.MethodAmount(
                rs.getString("method"),
                rs.getBigDecimal("amount")
        ));
    }

    private record Totals(
            BigDecimal amount,
            long count
    ) {
    }

    private static final String SALE_SCOPE = """
            FROM sale_payments payment
            JOIN sales sale ON sale.id = payment.sale_id
            WHERE sale.bookstore_id = :bookstoreId
              AND sale.status = 'COMPLETED'
              AND payment.created_at >= :from
              AND payment.created_at <= :to
            """;

    private static final String PURCHASE_SCOPE = """
            FROM purchase_payments payment
            JOIN purchases purchase ON purchase.id = payment.purchase_id
            WHERE purchase.bookstore_id = :bookstoreId
              AND purchase.status = 'CONFIRMED'
              AND payment.cancelled_at IS NULL
              AND payment.paid_at >= :from
              AND payment.paid_at <= :to
            """;

    private static final String SALE_TOTALS_SQL = """
            SELECT
                COALESCE(SUM(payment.amount), 0) AS amount,
                COUNT(*) AS payment_count
            """ + SALE_SCOPE;

    private static final String PURCHASE_TOTALS_SQL = """
            SELECT
                COALESCE(SUM(payment.amount), 0) AS amount,
                COUNT(*) AS payment_count
            """ + PURCHASE_SCOPE;

    private static final String SALE_BY_METHOD_SQL = """
            SELECT
                payment.method AS method,
                COALESCE(SUM(payment.amount), 0) AS amount
            """ + SALE_SCOPE + """
            GROUP BY payment.method
            ORDER BY payment.method
            """;

    private static final String PURCHASE_BY_METHOD_SQL = """
            SELECT
                payment.method AS method,
                COALESCE(SUM(payment.amount), 0) AS amount
            """ + PURCHASE_SCOPE + """
            GROUP BY payment.method
            ORDER BY payment.method
            """;
}
