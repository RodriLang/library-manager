package com.rodrilang.librarymanager.inventory.valuation.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class InventoryValuationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryValuationSnapshot summarize(
            Long bookstoreId,
            LocalDate asOf
    ) {
        String sql = """
                WITH current_prices AS (
                    SELECT DISTINCT ON (eep.book_id)
                        eep.book_id,
                        eep.price
                    FROM effective_editorial_prices eep
                    WHERE eep.active = TRUE
                      AND eep.valid_from <= :asOf
                    ORDER BY eep.book_id, eep.valid_from DESC, eep.id DESC
                ),
                active_layers AS (
                    SELECT
                        layer.quantity_remaining,
                        layer.cost_type,
                        layer.unit_cost,
                        layer.discount_percentage,
                        price.price AS current_price
                    FROM inventory_cost_layers layer
                    JOIN inventory inventory ON inventory.id = layer.inventory_id
                    LEFT JOIN current_prices price ON price.book_id = inventory.book_id
                    WHERE inventory.bookstore_id = :bookstoreId
                      AND layer.reversed_at IS NULL
                      AND layer.quantity_remaining > 0
                )
                SELECT
                    COALESCE(SUM(quantity_remaining), 0) AS total_units,

                    COALESCE(SUM(quantity_remaining)
                        FILTER (WHERE current_price IS NOT NULL), 0) AS current_price_units,
                    COALESCE(SUM(current_price * quantity_remaining)
                        FILTER (WHERE current_price IS NOT NULL), 0) AS known_retail_value_amount,

                    COALESCE(SUM(quantity_remaining)
                        FILTER (WHERE unit_cost IS NOT NULL), 0) AS historical_cost_units,
                    COALESCE(SUM(unit_cost * quantity_remaining)
                        FILTER (WHERE unit_cost IS NOT NULL), 0) AS known_historical_cost_amount,
                    COALESCE(SUM(unit_cost * quantity_remaining)
                        FILTER (WHERE cost_type = 'REAL' AND unit_cost IS NOT NULL), 0)
                        AS real_historical_cost_amount,
                    COALESCE(SUM(unit_cost * quantity_remaining)
                        FILTER (WHERE cost_type = 'ESTIMATED' AND unit_cost IS NOT NULL), 0)
                        AS estimated_historical_cost_amount,

                    COALESCE(SUM(quantity_remaining)
                        FILTER (
                            WHERE current_price IS NOT NULL
                              AND discount_percentage IS NOT NULL
                        ), 0) AS replacement_cost_units,
                    COALESCE(SUM(
                        current_price
                        * (1 - discount_percentage / 100.0)
                        * quantity_remaining
                    ) FILTER (
                        WHERE current_price IS NOT NULL
                          AND discount_percentage IS NOT NULL
                    ), 0) AS known_replacement_cost_amount
                FROM active_layers
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookstoreId", bookstoreId)
                .addValue("asOf", asOf);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new InventoryValuationSnapshot(
                rs.getLong("total_units"),
                rs.getLong("current_price_units"),
                rs.getBigDecimal("known_retail_value_amount"),
                rs.getLong("historical_cost_units"),
                rs.getBigDecimal("known_historical_cost_amount"),
                rs.getBigDecimal("real_historical_cost_amount"),
                rs.getBigDecimal("estimated_historical_cost_amount"),
                rs.getLong("replacement_cost_units"),
                rs.getBigDecimal("known_replacement_cost_amount")
        ));
    }
}
