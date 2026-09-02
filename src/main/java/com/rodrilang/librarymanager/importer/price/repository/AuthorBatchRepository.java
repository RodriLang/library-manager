package com.rodrilang.librarymanager.importer.price.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AuthorBatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void insertIfAbsentBatch(Map<String, String> originalNamesByNormalizedName) {

        if (originalNamesByNormalizedName == null || originalNamesByNormalizedName.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO authors (
                    name,
                    name_normalized,
                    created_at,
                    updated_at
                )
                VALUES (
                    :name,
                    :nameNormalized,
                    clock_timestamp(),
                    clock_timestamp()
                )
                ON CONFLICT (name_normalized)
                DO NOTHING
                """;

        MapSqlParameterSource[] parameters =
                originalNamesByNormalizedName
                        .entrySet()
                        .stream()
                        .map(entry ->
                                new MapSqlParameterSource()
                                        .addValue("name", entry.getValue(), Types.VARCHAR)
                                        .addValue("nameNormalized", entry.getKey(), Types.VARCHAR)
                        )
                        .toArray(MapSqlParameterSource[]::new);

        long startedAt = System.nanoTime();

        jdbcTemplate.batchUpdate(sql, parameters);

        log.info(
                "Author insert batch completed. rows={} time={}ms",
                originalNamesByNormalizedName.size(),
                (System.nanoTime() - startedAt) / 1_000_000
        );
    }
}