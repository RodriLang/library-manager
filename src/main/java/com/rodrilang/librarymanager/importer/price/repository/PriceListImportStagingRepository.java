package com.rodrilang.librarymanager.importer.price.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListStagingRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.StagingInsertResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;


@Repository
@RequiredArgsConstructor
public class PriceListImportStagingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StagingInsertResult insertBatch(
            Long jobId,
            List<PriceListRow> rows
    ) {
        if (rows.isEmpty()) {
            return new StagingInsertResult(0, 0);
        }

        String sql = """
                INSERT INTO price_list_import_staging_rows (
                    job_id,
                    row_number,
                    identifier_key,
                    row_payload,
                    valid,
                    validation_message
                )
                VALUES (
                    :jobId,
                    :rowNumber,
                    :identifierKey,
                    CAST(:payload AS jsonb),
                    TRUE,
                    NULL
                )
                ON CONFLICT DO NOTHING
                """;

        MapSqlParameterSource[] parameters = rows.stream()
                .map(row ->
                        new MapSqlParameterSource()
                                .addValue("jobId", jobId)
                                .addValue(
                                        "rowNumber",
                                        row.rowNumber()
                                )
                                .addValue(
                                        "identifierKey",
                                        resolveIdentifierKey(row)
                                )
                                .addValue(
                                        "payload",
                                        serialize(row)
                                )
                )
                .toArray(MapSqlParameterSource[]::new);

        int[] results =
                jdbcTemplate.batchUpdate(sql, parameters);

        int inserted = 0;

        for (int result : results) {
            if (result > 0) {
                inserted++;
            }
        }

        int duplicated = rows.size() - inserted;

        return new StagingInsertResult(
                inserted,
                duplicated
        );
    }

    public List<PriceListStagingRow> findValidBatch(
            Long jobId,
            long afterId,
            int limit
    ) {
        String sql = """
                SELECT id, row_payload
                FROM price_list_import_staging_rows
                WHERE job_id = :jobId
                  AND valid = TRUE
                  AND id > :afterId
                ORDER BY id
                LIMIT :limit
                """;

        return jdbcTemplate.query(
                sql,
                Map.of(
                        "jobId", jobId,
                        "afterId", afterId,
                        "limit", limit
                ),
                (resultSet, rowNum) ->
                        new PriceListStagingRow(
                                resultSet.getLong("id"),
                                deserialize(
                                        resultSet.getString("row_payload")
                                )
                        )
        );
    }

    public long findLastIdInBatch(
            Long jobId,
            long afterId,
            int limit
    ) {
        String sql = """
                SELECT COALESCE(MAX(id), :afterId)
                FROM (
                    SELECT id
                    FROM price_list_import_staging_rows
                    WHERE job_id = :jobId
                      AND valid = TRUE
                      AND id > :afterId
                    ORDER BY id
                    LIMIT :limit
                ) batch
                """;

        Long result = jdbcTemplate.queryForObject(
                sql,
                Map.of(
                        "jobId", jobId,
                        "afterId", afterId,
                        "limit", limit
                ),
                Long.class
        );

        return result == null ? afterId : result;
    }

    public long countValid(Long jobId) {
        return count(jobId, true);
    }

    public long countInvalid(Long jobId) {
        return count(jobId, false);
    }

    public void deleteByJobId(Long jobId) {
        jdbcTemplate.update(
                """
                        DELETE FROM price_list_import_staging_rows
                        WHERE job_id = :jobId
                        """,
                Map.of("jobId", jobId)
        );
    }

    private long count(Long jobId, boolean valid) {
        Long result = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM price_list_import_staging_rows
                        WHERE job_id = :jobId
                          AND valid = :valid
                        """,
                Map.of(
                        "jobId", jobId,
                        "valid", valid
                ),
                Long.class
        );

        return result == null ? 0 : result;
    }

    private String serialize(PriceListRow row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar una fila de importación.", exception);
        }
    }

    private PriceListRow deserialize(String json) {
        try {
            return objectMapper.readValue(
                    json,
                    PriceListRow.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "No se pudo reconstruir una fila de importación.",
                    exception
            );
        }
    }

    private String resolveIdentifierKey(PriceListRow row) {
        if (
                row.metadata() != null
                        && row.metadata().externalCode() != null
                        && !row.metadata().externalCode().isBlank()
        ) {
            return "EXTERNAL_CODE:"
                    + normalizeIdentifier(
                    row.metadata().externalCode()
            );
        }

        if (row.isbn() != null && !row.isbn().isBlank()) {
            return "ISBN:"
                    + normalizeIdentifier(row.isbn());
        }

        return null;
    }

    private String normalizeIdentifier(String value) {
        return value
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}