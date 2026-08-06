package com.rodrilang.librarymanager.importer.price.repository;

import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceInsertRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceUpdateRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EditorialPriceBatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void insertBatch(
            Long providerId,
            LocalDate validFrom,
            List<EditorialPriceInsertRow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO editorial_prices (
                    book_id,
                    provider_id,
                    price,
                    currency,
                    valid_from,
                    active
                )
                VALUES (
                    :bookId,
                    :providerId,
                    :price,
                    'ARS',
                    :validFrom,
                    TRUE
                )
                """;

        MapSqlParameterSource[] parameters = rows.stream()
                .map(row ->
                        new MapSqlParameterSource()
                                .addValue(
                                        "bookId",
                                        row.bookId(),
                                        Types.BIGINT
                                )
                                .addValue(
                                        "providerId",
                                        providerId,
                                        Types.BIGINT
                                )
                                .addValue(
                                        "price",
                                        row.price(),
                                        Types.NUMERIC
                                )
                                .addValue(
                                        "validFrom",
                                        validFrom,
                                        Types.DATE
                                )
                )
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(
                sql,
                parameters
        );

        long startedAt = System.nanoTime();

        jdbcTemplate.batchUpdate(
                sql,
                parameters
        );

        long elapsedMs =
                (System.nanoTime() - startedAt)
                        / 1_000_000;

        log.info(
                "Editorial price insert batch completed. "
                        + "providerId={} validFrom={} rows={} time={}ms",
                providerId,
                validFrom,
                rows.size(),
                elapsedMs
        );
    }

    public void updateBatch(
            List<EditorialPriceUpdateRow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        String sql = """
                UPDATE editorial_prices
                SET price = :price,
                    active = TRUE
                WHERE id = :editorialPriceId
                """;

        MapSqlParameterSource[] parameters = rows.stream()
                .map(row ->
                        new MapSqlParameterSource()
                                .addValue(
                                        "editorialPriceId",
                                        row.editorialPriceId(),
                                        Types.BIGINT
                                )
                                .addValue(
                                        "price",
                                        row.price(),
                                        Types.NUMERIC
                                )
                )
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(
                sql,
                parameters
        );
    }
}