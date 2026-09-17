package com.rodrilang.librarymanager.profitability.repository;

import com.rodrilang.librarymanager.profitability.repository.projection.ProfitabilityReportAggregateProjection;
import com.rodrilang.librarymanager.sales.model.Sale;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProfitabilityReportRepository extends Repository<Sale, Long> {

    @Query(value = """
            WITH sale_scope AS (
                SELECT s.id, s.total
                FROM sales s
                WHERE s.bookstore_id = :bookstoreId
                  AND s.status = 'COMPLETED'
                  AND s.sold_at >= :from
                  AND s.sold_at <= :to
            ),
            sale_aggregate AS (
                SELECT
                    COUNT(*) AS sale_count,
                    COALESCE(SUM(ss.total), 0) AS net_sales_amount
                FROM sale_scope ss
            ),
            unit_aggregate AS (
                SELECT COALESCE(SUM(si.quantity), 0) AS total_units
                FROM sale_items si
                JOIN sale_scope ss ON ss.id = si.sale_id
            ),
            cost_aggregate AS (
                SELECT
                    COALESCE(SUM(
                        CASE
                            WHEN layer.cost_type = 'REAL'
                             AND layer.unit_cost IS NOT NULL
                                THEN allocation.quantity * layer.unit_cost
                            ELSE 0
                        END
                    ), 0) AS real_cost_amount,
                    COALESCE(SUM(
                        CASE
                            WHEN layer.cost_type = 'ESTIMATED'
                             AND layer.unit_cost IS NOT NULL
                                THEN allocation.quantity * layer.unit_cost
                            ELSE 0
                        END
                    ), 0) AS estimated_cost_amount,
                    COALESCE(SUM(
                        CASE
                            WHEN layer.cost_type = 'REAL'
                             AND layer.unit_cost IS NOT NULL
                                THEN allocation.quantity
                            ELSE 0
                        END
                    ), 0) AS real_cost_units,
                    COALESCE(SUM(
                        CASE
                            WHEN layer.cost_type = 'ESTIMATED'
                             AND layer.unit_cost IS NOT NULL
                                THEN allocation.quantity
                            ELSE 0
                        END
                    ), 0) AS estimated_cost_units
                FROM inventory_cost_allocations allocation
                JOIN inventory_cost_layers layer
                  ON layer.id = allocation.cost_layer_id
                JOIN inventory_movements movement
                  ON movement.id = allocation.inventory_movement_id
                JOIN sale_scope ss
                  ON CAST(ss.id AS VARCHAR) = movement.reference_id
                WHERE movement.reference_type = 'SALE'
                  AND allocation.reversed_at IS NULL
            )
            SELECT
                sa.sale_count AS "saleCount",
                ua.total_units AS "totalUnits",
                sa.net_sales_amount AS "netSalesAmount",
                ca.real_cost_amount AS "realCostAmount",
                ca.estimated_cost_amount AS "estimatedCostAmount",
                ca.real_cost_units AS "realCostUnits",
                ca.estimated_cost_units AS "estimatedCostUnits"
            FROM sale_aggregate sa
            CROSS JOIN unit_aggregate ua
            CROSS JOIN cost_aggregate ca
            """, nativeQuery = true)
    ProfitabilityReportAggregateProjection summarize(
            @Param("bookstoreId") Long bookstoreId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
