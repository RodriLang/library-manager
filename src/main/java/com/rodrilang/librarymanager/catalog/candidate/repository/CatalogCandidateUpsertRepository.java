package com.rodrilang.librarymanager.catalog.candidate.repository;

import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class CatalogCandidateUpsertRepository {

    private final JdbcTemplate jdbcTemplate;

    public Long getOrCreate(ParsedIsbn isbn, Long bookstoreId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return jdbcTemplate.queryForObject("""
                        INSERT INTO catalog_candidates (
                            isbn_10,
                            isbn_13,
                            status,
                            first_seen_by_bookstore_id,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, 'PENDING', ?, ?, ?)
                        ON CONFLICT (isbn_13)
                        DO UPDATE SET isbn_10 = COALESCE(catalog_candidates.isbn_10, EXCLUDED.isbn_10)
                        RETURNING id
                        """,
                Long.class,
                isbn.isbn10(),
                isbn.isbn13(),
                bookstoreId,
                now,
                now
        );
    }
}
