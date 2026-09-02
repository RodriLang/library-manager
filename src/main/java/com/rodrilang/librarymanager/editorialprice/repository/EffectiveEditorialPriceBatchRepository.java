package com.rodrilang.librarymanager.editorialprice.repository;

import com.rodrilang.librarymanager.editorialprice.dto.internal.EffectiveEditorialPriceInsertRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EffectiveEditorialPriceBatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void insertBatch(List<EffectiveEditorialPriceInsertRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO effective_editorial_prices (
                    book_id,
                    price,
                    currency,
                    valid_from,
                    determination_type,
                    authority,
                    selected_editorial_price_id,
                    resolution_id,
                    active,
                    created_at
                )
                VALUES (
                    :bookId,
                    :price,
                    :currency,
                    :validFrom,
                    :determinationType,
                    :authority,
                    :selectedEditorialPriceId,
                    :resolutionId,
                    TRUE,
                    clock_timestamp()
                )
                """;

        MapSqlParameterSource[] parameters = rows.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("bookId", row.bookId(), Types.BIGINT)
                        .addValue("price", row.price(), Types.NUMERIC)
                        .addValue("currency", row.currency(), Types.VARCHAR)
                        .addValue("validFrom", row.validFrom(), Types.DATE)
                        .addValue("determinationType", row.determinationType().name(), Types.VARCHAR)
                        .addValue("authority", row.authority().name(), Types.VARCHAR)
                        .addValue("selectedEditorialPriceId", row.selectedEditorialPriceId(), Types.BIGINT)
                        .addValue("resolutionId", row.resolutionId(), Types.BIGINT)
                )
                .toArray(MapSqlParameterSource[]::new);

        long startedAt = System.nanoTime();

        jdbcTemplate.batchUpdate(sql, parameters);

        log.info(
                "Effective editorial price insert batch completed. rows={} time={}ms",
                rows.size(),
                (System.nanoTime() - startedAt) / 1_000_000
        );
    }
}