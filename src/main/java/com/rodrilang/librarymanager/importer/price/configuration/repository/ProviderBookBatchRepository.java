package com.rodrilang.librarymanager.importer.price.configuration.repository;

import com.rodrilang.librarymanager.importer.price.dto.internal.ProviderBookUpsertRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProviderBookBatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void upsertBatch(
            Long providerId,
            List<ProviderBookUpsertRow> rows,
            Instant now
    ) {
        if (rows.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO provider_books (
                    provider_id,
                    book_id,
                    external_code,
                    reported_isbn,
                    identifier_status,
                    active,
                    created_at,
                    updated_at,
                    last_seen_at
                )
                VALUES (
                    :providerId,
                    :bookId,
                    :externalCode,
                    :reportedIsbn,
                    :identifierStatus,
                    TRUE,
                    :now,
                    :now,
                    :now
                )
                ON CONFLICT (provider_id, book_id)
                DO UPDATE SET
                    external_code = EXCLUDED.external_code,
                    reported_isbn = EXCLUDED.reported_isbn,
                    identifier_status = EXCLUDED.identifier_status,
                    active = TRUE,
                    updated_at = CASE
                        WHEN provider_books.external_code
                                IS DISTINCT FROM EXCLUDED.external_code
                          OR provider_books.reported_isbn
                                IS DISTINCT FROM EXCLUDED.reported_isbn
                          OR provider_books.identifier_status
                                IS DISTINCT FROM EXCLUDED.identifier_status
                          OR provider_books.active = FALSE
                        THEN EXCLUDED.updated_at
                        ELSE provider_books.updated_at
                    END,
                    last_seen_at = EXCLUDED.last_seen_at
                """;

        Timestamp timestamp = Timestamp.from(now);

        MapSqlParameterSource[] parameters = rows.stream()
                .map(row ->
                        new MapSqlParameterSource()
                                .addValue("providerId", providerId)
                                .addValue("bookId", row.bookId())
                                .addValue("externalCode", row.externalCode())
                                .addValue("reportedIsbn", row.reportedIsbn())
                                .addValue(
                                        "identifierStatus",
                                        row.identifierStatus() != null
                                                ? row.identifierStatus().name()
                                                : null
                                )
                                .addValue("now", timestamp)
                )
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, parameters);
    }
}