package com.rodrilang.librarymanager.importer.price.repository;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListResolvedPrice;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListResolvedPriceCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PriceListImportPriceStagingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public int registerBatch(
            Long jobId,
            List<PriceListResolvedPriceCandidate> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        Set<Long> bookIds = candidates.stream()
                .map(PriceListResolvedPriceCandidate::bookId)
                .collect(Collectors.toSet());

        int existingCount =
                countExistingBooks(
                        jobId,
                        bookIds
                );

        String sql = """
                INSERT INTO price_list_import_price_staging (
                    job_id,
                    book_id,
                    selected_row_number,
                    selected_isbn,
                    selected_price,
                    first_row_number,
                    first_isbn,
                    first_price,
                    min_price,
                    max_price,
                    occurrence_count,
                    conflicting_price_count,
                    created_at,
                    updated_at
                )
                VALUES (
                    :jobId,
                    :bookId,
                    :rowNumber,
                    :isbn,
                    :price,
                    :rowNumber,
                    :isbn,
                    :price,
                    :price,
                    :price,
                    1,
                    0,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (job_id, book_id)
                DO UPDATE SET
                    occurrence_count =
                        price_list_import_price_staging.occurrence_count + 1,
                
                    conflicting_price_count =
                        price_list_import_price_staging.conflicting_price_count
                        +
                        CASE
                            WHEN price_list_import_price_staging.selected_price
                                    IS DISTINCT FROM EXCLUDED.selected_price
                            THEN 1
                            ELSE 0
                        END,
                
                    min_price =
                        LEAST(
                            price_list_import_price_staging.min_price,
                            EXCLUDED.selected_price
                        ),
                
                    max_price =
                        GREATEST(
                            price_list_import_price_staging.max_price,
                            EXCLUDED.selected_price
                        ),
                
                    selected_row_number =
                        CASE
                            WHEN EXCLUDED.selected_price
                                    > price_list_import_price_staging.selected_price
                            THEN EXCLUDED.selected_row_number
                            ELSE price_list_import_price_staging.selected_row_number
                        END,
                
                    selected_isbn =
                        CASE
                            WHEN EXCLUDED.selected_price
                                    > price_list_import_price_staging.selected_price
                            THEN EXCLUDED.selected_isbn
                            ELSE price_list_import_price_staging.selected_isbn
                        END,
                
                    selected_price =
                        GREATEST(
                            price_list_import_price_staging.selected_price,
                            EXCLUDED.selected_price
                        ),
                
                    updated_at = CURRENT_TIMESTAMP
                """;

        MapSqlParameterSource[] parameters =
                candidates.stream()
                        .map(candidate ->
                                new MapSqlParameterSource()
                                        .addValue("jobId", jobId)
                                        .addValue("bookId", candidate.bookId())
                                        .addValue("rowNumber", candidate.rowNumber())
                                        .addValue("isbn", candidate.isbn())
                                        .addValue("price", candidate.price())
                        )
                        .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(
                sql,
                parameters
        );

        return bookIds.size() - existingCount;
    }

    public List<PriceListResolvedPrice> findBatch(
            Long jobId,
            long afterId,
            int limit
    ) {
        String sql = """
                SELECT
                    id,
                    book_id,
                    selected_row_number,
                    selected_isbn,
                    selected_price,
                
                    first_row_number,
                    first_isbn,
                    first_price,
                
                    min_price,
                    max_price,
                
                    occurrence_count,
                    conflicting_price_count
                
                FROM price_list_import_price_staging
                
                WHERE job_id = :jobId
                  AND id > :afterId
                
                ORDER BY id
                
                LIMIT :limit
                """;

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("afterId", afterId)
                        .addValue("limit", limit);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) ->
                        new PriceListResolvedPrice(
                                rs.getLong("book_id"),

                                rs.getLong("id"),
                                rs.getInt("selected_row_number"),
                                rs.getString("selected_isbn"),
                                rs.getBigDecimal("selected_price"),

                                rs.getInt("first_row_number"),
                                rs.getString("first_isbn"),
                                rs.getBigDecimal("first_price"),

                                rs.getBigDecimal("min_price"),
                                rs.getBigDecimal("max_price"),

                                rs.getInt("occurrence_count"),
                                rs.getInt("conflicting_price_count")
                        )
        );
    }

    public void deleteByJobId(Long jobId) {
        String sql = """
                DELETE FROM price_list_import_price_staging
                WHERE job_id = :jobId
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource(
                        "jobId",
                        jobId
                )
        );
    }

    public int countByJobId(Long jobId) {
        String sql = """
                SELECT COUNT(*)
                FROM price_list_import_price_staging
                WHERE job_id = :jobId
                """;

        Integer result =
                jdbcTemplate.queryForObject(
                        sql,
                        new MapSqlParameterSource(
                                "jobId",
                                jobId
                        ),
                        Integer.class
                );

        return result != null
                ? result
                : 0;
    }

    private int countExistingBooks(
            Long jobId,
            Set<Long> bookIds
    ) {
        if (bookIds.isEmpty()) {
            return 0;
        }

        String sql = """
                SELECT COUNT(*)
                FROM price_list_import_price_staging
                WHERE job_id = :jobId
                  AND book_id IN (:bookIds)
                """;

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("bookIds", bookIds);

        Integer result =
                jdbcTemplate.queryForObject(
                        sql,
                        params,
                        Integer.class
                );

        return result != null
                ? result
                : 0;
    }
}