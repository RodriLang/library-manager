package com.rodrilang.librarymanager.admin.price.controller;

import com.rodrilang.librarymanager.admin.price.dto.AdminPriceAnalyticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.List;

@RestController
@RequestMapping("/api/admin/prices/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPriceAnalyticsController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public AdminPriceAnalyticsResponse analytics(@RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.max(1, Math.min(days, 3650));
        String cte = """
                WITH history AS (
                    SELECT e.book_id, e.price, e.valid_from,
                           lag(e.price) OVER (PARTITION BY e.book_id ORDER BY e.valid_from, e.id) AS previous_price
                    FROM effective_editorial_prices e
                    WHERE e.active = true AND e.valid_from <= CURRENT_DATE
                ), changes AS (
                    SELECT * FROM history
                    WHERE previous_price IS NOT NULL
                      AND valid_from >= CURRENT_DATE - (? * INTERVAL '1 day')
                )
                """;

        var stats = jdbc.queryForMap(cte + """
                SELECT
                  count(*) FILTER (WHERE price > previous_price) AS increases,
                  count(*) FILTER (WHERE price < previous_price) AS decreases,
                  count(*) FILTER (WHERE price = previous_price) AS unchanged,
                  avg(((price - previous_price) / nullif(previous_price, 0)) * 100) AS avg_change
                FROM changes
                """, safeDays);

        long increases = number(stats.get("increases")).longValue();
        long decreases = number(stats.get("decreases")).longValue();
        long unchanged = number(stats.get("unchanged")).longValue();
        BigDecimal average = decimal(stats.get("avg_change"));

        List<AdminPriceAnalyticsResponse.PriceChange> largest = jdbc.query(cte + """
                SELECT c.book_id, b.title, coalesce(b.isbn_13, b.isbn_10) isbn,
                       c.previous_price, c.price, c.valid_from,
                       ((c.price - c.previous_price) / nullif(c.previous_price, 0)) * 100 pct
                FROM changes c
                JOIN books b ON b.id = c.book_id
                WHERE c.price <> c.previous_price
                ORDER BY abs(((c.price - c.previous_price) / nullif(c.previous_price, 0)) * 100) DESC NULLS LAST
                LIMIT 20
                """, (rs, rowNum) -> new AdminPriceAnalyticsResponse.PriceChange(
                rs.getLong("book_id"), rs.getString("title"), rs.getString("isbn"),
                rs.getBigDecimal("previous_price"), rs.getBigDecimal("price"),
                rs.getBigDecimal("pct") == null ? BigDecimal.ZERO : rs.getBigDecimal("pct").setScale(2, RoundingMode.HALF_UP),
                rs.getObject("valid_from", java.time.LocalDate.class)
        ), safeDays);

        return new AdminPriceAnalyticsResponse(safeDays, increases, decreases, unchanged,
                average.setScale(2, RoundingMode.HALF_UP), largest);
    }

    private Number number(Object value) { return value instanceof Number n ? n : 0L; }
    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
