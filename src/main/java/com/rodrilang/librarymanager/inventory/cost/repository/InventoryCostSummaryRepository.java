package com.rodrilang.librarymanager.inventory.cost.repository;

import com.rodrilang.librarymanager.inventory.cost.repository.projection.InventoryCostSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryCostSummaryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryCostSummaryProjection summarize(Long bookstoreId) {
        String sql = """
                SELECT
                    COALESCE(SUM(layer.quantity_remaining), 0) AS current_units,
                    COALESCE(SUM(layer.quantity_remaining) FILTER (WHERE layer.unit_cost IS NOT NULL), 0) AS known_cost_units,
                    COALESCE(SUM(layer.quantity_remaining) FILTER (WHERE layer.unit_cost IS NULL), 0) AS unknown_cost_units,
                    COALESCE(SUM(layer.quantity_remaining) FILTER (WHERE layer.cost_type = 'REAL'), 0) AS real_cost_units,
                    COALESCE(SUM(layer.quantity_remaining) FILTER (WHERE layer.cost_type = 'ESTIMATED'), 0) AS estimated_cost_units,
                    COUNT(*) FILTER (WHERE layer.unit_cost IS NULL) AS unknown_cost_layers,
                    COUNT(*) FILTER (WHERE layer.discount_percentage IS NULL) AS missing_discount_layers
                FROM inventory_cost_layers layer
                JOIN inventory inventory ON inventory.id = layer.inventory_id
                WHERE inventory.bookstore_id = :bookstoreId
                  AND layer.reversed_at IS NULL
                """;

        MapSqlParameterSource params = new MapSqlParameterSource("bookstoreId", bookstoreId);
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Summary(
                rs.getLong("current_units"),
                rs.getLong("known_cost_units"),
                rs.getLong("unknown_cost_units"),
                rs.getLong("real_cost_units"),
                rs.getLong("estimated_cost_units"),
                rs.getLong("unknown_cost_layers"),
                rs.getLong("missing_discount_layers")
        ));
    }

    private record Summary(
            Long currentUnits,
            Long knownCostUnits,
            Long unknownCostUnits,
            Long realCostUnits,
            Long estimatedCostUnits,
            Long unknownCostLayers,
            Long missingDiscountLayers
    ) implements InventoryCostSummaryProjection {

        @Override
        public Long getCurrentUnits() {
            return currentUnits;
        }

        @Override
        public Long getKnownCostUnits() {
            return knownCostUnits;
        }

        @Override
        public Long getUnknownCostUnits() {
            return unknownCostUnits;
        }

        @Override
        public Long getRealCostUnits() {
            return realCostUnits;
        }

        @Override
        public Long getEstimatedCostUnits() {
            return estimatedCostUnits;
        }

        @Override
        public Long getUnknownCostLayers() {
            return unknownCostLayers;
        }

        @Override
        public Long getMissingDiscountLayers() {
            return missingDiscountLayers;
        }
    }
}
