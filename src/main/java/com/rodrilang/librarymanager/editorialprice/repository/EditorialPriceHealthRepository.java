package com.rodrilang.librarymanager.editorialprice.repository;

import com.rodrilang.librarymanager.editorialprice.dto.internal.EditorialPriceHealthSummaryCounts;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceHealthIssueResponse;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceConflictScope;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceHealthIssueType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class EditorialPriceHealthRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EditorialPriceHealthSummaryCounts getSummary(LocalDate today, LocalDate staleBefore) {
        LocalDate nextPeriodStart = YearMonth.from(today).plusMonths(1).atDay(1);

        MapSqlParameterSource params = baseParams(today, staleBefore)
                .addValue("nextPeriodStart", nextPeriodStart);

        String sql = """
                WITH current_effective AS (
                    SELECT DISTINCT ON (eep.book_id)
                        eep.book_id,
                        eep.price,
                        eep.currency,
                        eep.valid_from,
                        eep.authority
                    FROM effective_editorial_prices eep
                    WHERE eep.active = TRUE
                      AND eep.valid_from <= :today
                    ORDER BY eep.book_id, eep.valid_from DESC, eep.id DESC
                ),
                eligible_official_sources AS (
                    SELECT
                        ep.id,
                        ep.book_id,
                        ep.price,
                        ep.currency,
                        ep.valid_from,
                        ep.provider_id,
                        ep.origin
                    FROM editorial_prices ep
                    WHERE ep.active = TRUE
                      AND ep.valid_from <= :today
                      AND ep.origin IN ('PRICE_LIST', 'MANUAL_DISTRIBUTOR', 'MANUAL_PUBLISHER')
                      AND NOT (
                          ep.origin = 'PRICE_LIST'
                          AND ep.provider_id IS NOT NULL
                          AND EXISTS (
                              SELECT 1
                              FROM editorial_prices manual
                              WHERE manual.book_id = ep.book_id
                                AND manual.provider_id = ep.provider_id
                                AND manual.valid_from = ep.valid_from
                                AND manual.active = TRUE
                                AND manual.origin = 'MANUAL_DISTRIBUTOR'
                          )
                      )
                ),
                latest_official_dates AS (
                    SELECT book_id, MAX(valid_from) AS valid_from
                    FROM eligible_official_sources
                    GROUP BY book_id
                ),
                conflict_books AS (
                    SELECT eos.book_id
                    FROM eligible_official_sources eos
                    JOIN latest_official_dates lod
                      ON lod.book_id = eos.book_id
                     AND lod.valid_from = eos.valid_from
                    JOIN books b ON b.id = eos.book_id AND b.active = TRUE
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM editorial_price_resolutions r
                        WHERE r.book_id = eos.book_id
                          AND r.valid_from = eos.valid_from
                          AND r.active = TRUE
                    )
                    GROUP BY eos.book_id, eos.valid_from
                    HAVING MIN(eos.price) IS DISTINCT FROM MAX(eos.price)
                        OR MIN(COALESCE(UPPER(eos.currency), ''))
                           IS DISTINCT FROM
                           MAX(COALESCE(UPPER(eos.currency), ''))
                ),
                next_period_official_sources AS (
                    SELECT
                        ep.id,
                        ep.book_id,
                        ep.price,
                        ep.currency,
                        ep.valid_from,
                        ep.provider_id,
                        ep.origin
                    FROM editorial_prices ep
                    WHERE ep.active = TRUE
                      AND ep.valid_from = :nextPeriodStart
                      AND ep.origin IN ('PRICE_LIST', 'MANUAL_DISTRIBUTOR', 'MANUAL_PUBLISHER')
                      AND NOT (
                          ep.origin = 'PRICE_LIST'
                          AND ep.provider_id IS NOT NULL
                          AND EXISTS (
                              SELECT 1
                              FROM editorial_prices manual
                              WHERE manual.book_id = ep.book_id
                                AND manual.provider_id = ep.provider_id
                                AND manual.valid_from = ep.valid_from
                                AND manual.active = TRUE
                                AND manual.origin = 'MANUAL_DISTRIBUTOR'
                          )
                      )
                ),
                next_conflict_books AS (
                    SELECT eos.book_id
                    FROM next_period_official_sources eos
                    JOIN books b ON b.id = eos.book_id AND b.active = TRUE
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM editorial_price_resolutions r
                        WHERE r.book_id = eos.book_id
                          AND r.valid_from = eos.valid_from
                          AND r.active = TRUE
                    )
                    GROUP BY eos.book_id, eos.valid_from
                    HAVING MIN(eos.price) IS DISTINCT FROM MAX(eos.price)
                        OR MIN(COALESCE(UPPER(eos.currency), ''))
                           IS DISTINCT FROM
                           MAX(COALESCE(UPPER(eos.currency), ''))
                ),
                no_current_books AS (
                    SELECT b.id AS book_id
                    FROM books b
                    LEFT JOIN current_effective ce ON ce.book_id = b.id
                    WHERE b.active = TRUE
                      AND ce.book_id IS NULL
                ),
                future_only_books AS (
                    SELECT nc.book_id
                    FROM no_current_books nc
                    WHERE EXISTS (
                        SELECT 1
                        FROM editorial_prices ep
                        WHERE ep.book_id = nc.book_id
                          AND ep.active = TRUE
                          AND ep.valid_from > :today
                    )
                ),
                no_price_books AS (
                    SELECT nc.book_id
                    FROM no_current_books nc
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM editorial_prices ep
                        WHERE ep.book_id = nc.book_id
                          AND ep.active = TRUE
                          AND ep.valid_from > :today
                    )
                ),
                stale_books AS (
                    SELECT ce.book_id
                    FROM current_effective ce
                    JOIN books b ON b.id = ce.book_id AND b.active = TRUE
                    WHERE ce.authority = 'OFFICIAL'
                      AND ce.valid_from < :staleBefore
                      AND NOT EXISTS (
                          SELECT 1
                          FROM editorial_prices ep
                          WHERE ep.book_id = ce.book_id
                            AND ep.active = TRUE
                            AND ep.valid_from <= :today
                            AND ep.origin IN ('PRICE_LIST', 'MANUAL_DISTRIBUTOR', 'MANUAL_PUBLISHER')
                            AND ep.price = ce.price
                            AND UPPER(ep.currency) = UPPER(ce.currency)
                            AND NOT (
                                ep.origin = 'PRICE_LIST'
                                AND ep.provider_id IS NOT NULL
                                AND EXISTS (
                                    SELECT 1
                                    FROM editorial_prices manual
                                    WHERE manual.book_id = ep.book_id
                                      AND manual.provider_id = ep.provider_id
                                      AND manual.valid_from = ep.valid_from
                                      AND manual.active = TRUE
                                      AND manual.origin = 'MANUAL_DISTRIBUTOR'
                                )
                            )
                            AND (
                                ep.valid_from >= :staleBefore
                                OR EXISTS (
                                    SELECT 1
                                    FROM editorial_price_confirmations c
                                    WHERE c.editorial_price_id = ep.id
                                      AND c.confirmed_on >= :staleBefore
                                      AND c.confirmed_on <= :today
                                )
                            )
                      )
                ),
                external_books AS (
                    SELECT ce.book_id
                    FROM current_effective ce
                    JOIN books b ON b.id = ce.book_id
                    WHERE b.active = TRUE
                      AND ce.authority = 'EXTERNAL_REFERENCE'
                ),
                all_issue_books AS (
                    SELECT book_id FROM conflict_books
                    UNION
                    SELECT book_id FROM no_price_books
                    UNION
                    SELECT book_id FROM future_only_books
                    UNION
                    SELECT book_id FROM stale_books
                    UNION
                    SELECT book_id FROM external_books
                )
                SELECT
                    (SELECT COUNT(*) FROM all_issue_books) AS total_books_with_issues,
                    (SELECT COUNT(*) FROM conflict_books) AS source_conflict,
                    (SELECT COUNT(*) FROM next_conflict_books) AS next_period_source_conflict,
                    (SELECT COUNT(*) FROM no_price_books) AS no_current_price,
                    (SELECT COUNT(*) FROM future_only_books) AS future_only,
                    (SELECT COUNT(*) FROM stale_books) AS stale_evidence,
                    (SELECT COUNT(*) FROM external_books) AS external_reference_only
                """;

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) ->
                new EditorialPriceHealthSummaryCounts(
                        rs.getLong("total_books_with_issues"),
                        rs.getLong("source_conflict"),
                        rs.getLong("next_period_source_conflict"),
                        rs.getLong("no_current_price"),
                        rs.getLong("future_only"),
                        rs.getLong("stale_evidence"),
                        rs.getLong("external_reference_only")
                )
        );
    }

    public Page<EditorialPriceHealthIssueResponse> findIssues(
            EditorialPriceHealthIssueType type,
            EditorialPriceConflictScope conflictScope,
            String query,
            LocalDate today,
            LocalDate staleBefore,
            Pageable pageable
    ) {
        return switch (type) {
            case SOURCE_CONFLICT -> conflictScope == EditorialPriceConflictScope.NEXT_PERIOD
                    ? findNextPeriodSourceConflicts(query, today, pageable)
                    : findCurrentSourceConflicts(query, today, pageable);
            case NO_CURRENT_PRICE -> findNoCurrentPrices(query, today, pageable);
            case FUTURE_ONLY -> findFutureOnly(query, today, pageable);
            case STALE_EVIDENCE -> findStaleEvidence(query, today, staleBefore, pageable);
            case EXTERNAL_REFERENCE_ONLY -> findExternalReferenceOnly(query, today, pageable);
        };
    }

    private MapSqlParameterSource baseParams(LocalDate today, LocalDate staleBefore) {
        return new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("staleBefore", staleBefore);
    }

    private EditorialPriceHealthIssueResponse mapIssue(ResultSet rs, int rowNum) throws SQLException {
        return new EditorialPriceHealthIssueResponse(
                rs.getLong("book_id"),
                rs.getString("title"),
                rs.getString("isbn"),
                rs.getString("publisher_name"),
                EditorialPriceHealthIssueType.valueOf(rs.getString("issue_type")),
                rs.getBigDecimal("current_price"),
                rs.getString("currency"),
                rs.getObject("current_valid_from", LocalDate.class),
                rs.getObject("conflict_valid_from", LocalDate.class),
                rs.getObject("next_valid_from", LocalDate.class),
                rs.getObject("last_evidence_on", LocalDate.class)
        );
    }

    private Page<EditorialPriceHealthIssueResponse> findCurrentSourceConflicts(String query, LocalDate today, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("today", today);
        StringBuilder search = new StringBuilder();
        appendSearchFilter(search, params, query);

        String sql = """
                WITH eligible_official_sources AS (
                    SELECT ep.*
                    FROM editorial_prices ep
                    WHERE ep.active = TRUE
                      AND ep.valid_from <= :today
                      AND ep.origin IN ('PRICE_LIST', 'MANUAL_DISTRIBUTOR', 'MANUAL_PUBLISHER')
                      AND NOT (
                          ep.origin = 'PRICE_LIST'
                          AND ep.provider_id IS NOT NULL
                          AND EXISTS (
                              SELECT 1
                              FROM editorial_prices manual
                              WHERE manual.book_id = ep.book_id
                                AND manual.provider_id = ep.provider_id
                                AND manual.valid_from = ep.valid_from
                                AND manual.active = TRUE
                                AND manual.origin = 'MANUAL_DISTRIBUTOR'
                          )
                      )
                ),
                latest_official_dates AS (
                    SELECT book_id, MAX(valid_from) AS valid_from
                    FROM eligible_official_sources
                    GROUP BY book_id
                ),
                current_conflicts AS (
                    SELECT eos.book_id, eos.valid_from
                    FROM eligible_official_sources eos
                    JOIN latest_official_dates lod
                      ON lod.book_id = eos.book_id
                     AND lod.valid_from = eos.valid_from
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM editorial_price_resolutions r
                        WHERE r.book_id = eos.book_id
                          AND r.valid_from = eos.valid_from
                          AND r.active = TRUE
                    )
                    GROUP BY eos.book_id, eos.valid_from
                    HAVING MIN(eos.price) IS DISTINCT FROM MAX(eos.price)
                        OR MIN(COALESCE(UPPER(eos.currency), ''))
                           IS DISTINCT FROM
                           MAX(COALESCE(UPPER(eos.currency), ''))
                ),
                filtered AS (
                    SELECT
                        b.id AS book_id,
                        b.title,
                        COALESCE(b.isbn_13, b.isbn_10) AS isbn,
                        p.name AS publisher_name,
                        'SOURCE_CONFLICT'::varchar AS issue_type,
                        ce.price AS current_price,
                        ce.currency,
                        ce.valid_from AS current_valid_from,
                        cc.valid_from AS conflict_valid_from,
                        NULL::date AS next_valid_from,
                        NULL::date AS last_evidence_on
                    FROM current_conflicts cc
                    JOIN books b ON b.id = cc.book_id AND b.active = TRUE
                    LEFT JOIN publishers p ON p.id = b.publisher_id
                    LEFT JOIN LATERAL (
                        SELECT eep.price, eep.currency, eep.valid_from
                        FROM effective_editorial_prices eep
                        WHERE eep.book_id = b.id
                          AND eep.active = TRUE
                          AND eep.valid_from <= :today
                        ORDER BY eep.valid_from DESC, eep.id DESC
                        LIMIT 1
                    ) ce ON TRUE
                    WHERE 1 = 1
                """ + search + """
                )
                SELECT
                    f.book_id,
                    f.title,
                    f.isbn,
                    f.publisher_name,
                    f.issue_type,
                    f.current_price,
                    f.currency,
                    f.current_valid_from,
                    f.conflict_valid_from,
                    f.next_valid_from,
                    f.last_evidence_on,
                    COUNT(*) OVER() AS total_count
                FROM filtered f
                ORDER BY f.conflict_valid_from DESC, f.title ASC, f.book_id ASC
                LIMIT :limit OFFSET :offset
                """;

        return queryPage(sql, params, pageable);
    }

    private Page<EditorialPriceHealthIssueResponse> findNextPeriodSourceConflicts(
            String query,
            LocalDate today,
            Pageable pageable
    ) {
        LocalDate nextPeriodStart = YearMonth.from(today).plusMonths(1).atDay(1);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("nextPeriodStart", nextPeriodStart);

        StringBuilder search = new StringBuilder();
        appendSearchFilter(search, params, query);

        String sql = """
                WITH eligible_official_sources AS (
                    SELECT ep.*
                    FROM editorial_prices ep
                    WHERE ep.active = TRUE
                      AND ep.valid_from = :nextPeriodStart
                      AND ep.origin IN ('PRICE_LIST', 'MANUAL_DISTRIBUTOR', 'MANUAL_PUBLISHER')
                      AND NOT (
                          ep.origin = 'PRICE_LIST'
                          AND ep.provider_id IS NOT NULL
                          AND EXISTS (
                              SELECT 1
                              FROM editorial_prices manual
                              WHERE manual.book_id = ep.book_id
                                AND manual.provider_id = ep.provider_id
                                AND manual.valid_from = ep.valid_from
                                AND manual.active = TRUE
                                AND manual.origin = 'MANUAL_DISTRIBUTOR'
                          )
                      )
                ),
                next_period_conflicts AS (
                    SELECT eos.book_id, eos.valid_from
                    FROM eligible_official_sources eos
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM editorial_price_resolutions r
                        WHERE r.book_id = eos.book_id
                          AND r.valid_from = eos.valid_from
                          AND r.active = TRUE
                    )
                    GROUP BY eos.book_id, eos.valid_from
                    HAVING MIN(eos.price) IS DISTINCT FROM MAX(eos.price)
                        OR MIN(COALESCE(UPPER(eos.currency), ''))
                           IS DISTINCT FROM
                           MAX(COALESCE(UPPER(eos.currency), ''))
                ),
                filtered AS (
                    SELECT
                        b.id AS book_id,
                        b.title,
                        COALESCE(b.isbn_13, b.isbn_10) AS isbn,
                        p.name AS publisher_name,
                        'SOURCE_CONFLICT'::varchar AS issue_type,
                        ce.price AS current_price,
                        ce.currency,
                        ce.valid_from AS current_valid_from,
                        npc.valid_from AS conflict_valid_from,
                        npc.valid_from AS next_valid_from,
                        NULL::date AS last_evidence_on
                    FROM next_period_conflicts npc
                    JOIN books b ON b.id = npc.book_id AND b.active = TRUE
                    LEFT JOIN publishers p ON p.id = b.publisher_id
                    LEFT JOIN LATERAL (
                        SELECT eep.price, eep.currency, eep.valid_from
                        FROM effective_editorial_prices eep
                        WHERE eep.book_id = b.id
                          AND eep.active = TRUE
                          AND eep.valid_from <= :today
                        ORDER BY eep.valid_from DESC, eep.id DESC
                        LIMIT 1
                    ) ce ON TRUE
                    WHERE 1 = 1
                """ + search + """
                )
                SELECT
                    f.book_id,
                    f.title,
                    f.isbn,
                    f.publisher_name,
                    f.issue_type,
                    f.current_price,
                    f.currency,
                    f.current_valid_from,
                    f.conflict_valid_from,
                    f.next_valid_from,
                    f.last_evidence_on,
                    COUNT(*) OVER() AS total_count
                FROM filtered f
                ORDER BY f.title ASC, f.book_id ASC
                LIMIT :limit OFFSET :offset
                """;

        return queryPage(sql, params, pageable);
    }

    private Page<EditorialPriceHealthIssueResponse> findNoCurrentPrices(String query, LocalDate today, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("today", today);
        StringBuilder search = new StringBuilder();
        appendSearchFilter(search, params, query);

        String sql = """
                SELECT
                    b.id AS book_id,
                    b.title,
                    COALESCE(b.isbn_13, b.isbn_10) AS isbn,
                    p.name AS publisher_name,
                    'NO_CURRENT_PRICE'::varchar AS issue_type,
                    NULL::numeric AS current_price,
                    NULL::varchar AS currency,
                    NULL::date AS current_valid_from,
                    NULL::date AS conflict_valid_from,
                    NULL::date AS next_valid_from,
                    NULL::date AS last_evidence_on,
                    COUNT(*) OVER() AS total_count
                FROM books b
                LEFT JOIN publishers p ON p.id = b.publisher_id
                WHERE b.active = TRUE
                  AND NOT EXISTS (
                      SELECT 1
                      FROM effective_editorial_prices eep
                      WHERE eep.book_id = b.id
                        AND eep.active = TRUE
                        AND eep.valid_from <= :today
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM editorial_prices ep
                      WHERE ep.book_id = b.id
                        AND ep.active = TRUE
                        AND ep.valid_from > :today
                  )
                """ + search + """
                ORDER BY b.title ASC, b.id ASC
                LIMIT :limit OFFSET :offset
                """;

        return queryPage(sql, params, pageable);
    }

    private Page<EditorialPriceHealthIssueResponse> findFutureOnly(String query, LocalDate today, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("today", today);
        StringBuilder search = new StringBuilder();
        appendSearchFilter(search, params, query);

        String sql = """
                SELECT
                    b.id AS book_id,
                    b.title,
                    COALESCE(b.isbn_13, b.isbn_10) AS isbn,
                    p.name AS publisher_name,
                    'FUTURE_ONLY'::varchar AS issue_type,
                    NULL::numeric AS current_price,
                    NULL::varchar AS currency,
                    NULL::date AS current_valid_from,
                    NULL::date AS conflict_valid_from,
                    fi.next_valid_from,
                    NULL::date AS last_evidence_on,
                    COUNT(*) OVER() AS total_count
                FROM books b
                LEFT JOIN publishers p ON p.id = b.publisher_id
                JOIN LATERAL (
                    SELECT ep.valid_from AS next_valid_from
                    FROM editorial_prices ep
                    WHERE ep.book_id = b.id
                      AND ep.active = TRUE
                      AND ep.valid_from > :today
                    ORDER BY ep.valid_from ASC, ep.id ASC
                    LIMIT 1
                ) fi ON TRUE
                WHERE b.active = TRUE
                  AND NOT EXISTS (
                      SELECT 1
                      FROM effective_editorial_prices eep
                      WHERE eep.book_id = b.id
                        AND eep.active = TRUE
                        AND eep.valid_from <= :today
                  )
                """ + search + """
                ORDER BY fi.next_valid_from ASC, b.title ASC, b.id ASC
                LIMIT :limit OFFSET :offset
                """;

        return queryPage(sql, params, pageable);
    }

    private Page<EditorialPriceHealthIssueResponse> findStaleEvidence(
            String query,
            LocalDate today,
            LocalDate staleBefore,
            Pageable pageable
    ) {
        MapSqlParameterSource params = baseParams(today, staleBefore);
        StringBuilder search = new StringBuilder();
        appendSearchFilter(search, params, query);

        String sql = """
                WITH current_effective AS (
                    SELECT DISTINCT ON (eep.book_id)
                        eep.book_id,
                        eep.price,
                        eep.currency,
                        eep.valid_from,
                        eep.authority
                    FROM effective_editorial_prices eep
                    WHERE eep.active = TRUE
                      AND eep.valid_from <= :today
                    ORDER BY eep.book_id, eep.valid_from DESC, eep.id DESC
                ),
                evidence AS (
                    SELECT
                        ce.book_id,
                        ce.price,
                        ce.currency,
                        ce.valid_from,
                        GREATEST(
                            ce.valid_from,
                            COALESCE(ev.last_evidence_on, ce.valid_from)
                        ) AS last_evidence_on
                    FROM current_effective ce
                    LEFT JOIN LATERAL (
                        SELECT MAX(
                            GREATEST(
                                ep.valid_from,
                                COALESCE(conf.last_confirmed_on, ep.valid_from)
                            )
                        ) AS last_evidence_on
                        FROM editorial_prices ep
                        LEFT JOIN LATERAL (
                            SELECT MAX(c.confirmed_on) AS last_confirmed_on
                            FROM editorial_price_confirmations c
                            WHERE c.editorial_price_id = ep.id
                              AND c.confirmed_on <= :today
                        ) conf ON TRUE
                        WHERE ep.book_id = ce.book_id
                          AND ep.active = TRUE
                          AND ep.valid_from <= :today
                          AND ep.origin IN ('PRICE_LIST', 'MANUAL_DISTRIBUTOR', 'MANUAL_PUBLISHER')
                          AND ep.price = ce.price
                          AND UPPER(ep.currency) = UPPER(ce.currency)
                          AND NOT (
                              ep.origin = 'PRICE_LIST'
                              AND ep.provider_id IS NOT NULL
                              AND EXISTS (
                                  SELECT 1
                                  FROM editorial_prices manual
                                  WHERE manual.book_id = ep.book_id
                                    AND manual.provider_id = ep.provider_id
                                    AND manual.valid_from = ep.valid_from
                                    AND manual.active = TRUE
                                    AND manual.origin = 'MANUAL_DISTRIBUTOR'
                              )
                          )
                    ) ev ON TRUE
                    WHERE ce.authority = 'OFFICIAL'
                      AND ce.valid_from < :staleBefore
                ),
                stale AS (
                    SELECT *
                    FROM evidence
                    WHERE last_evidence_on < :staleBefore
                )
                SELECT
                    b.id AS book_id,
                    b.title,
                    COALESCE(b.isbn_13, b.isbn_10) AS isbn,
                    p.name AS publisher_name,
                    'STALE_EVIDENCE'::varchar AS issue_type,
                    s.price AS current_price,
                    s.currency,
                    s.valid_from AS current_valid_from,
                    NULL::date AS conflict_valid_from,
                    NULL::date AS next_valid_from,
                    s.last_evidence_on,
                    COUNT(*) OVER() AS total_count
                FROM stale s
                JOIN books b ON b.id = s.book_id AND b.active = TRUE
                LEFT JOIN publishers p ON p.id = b.publisher_id
                WHERE 1 = 1
                """ + search + """
                ORDER BY s.last_evidence_on ASC, b.title ASC, b.id ASC
                LIMIT :limit OFFSET :offset
                """;

        return queryPage(sql, params, pageable);
    }

    private Page<EditorialPriceHealthIssueResponse> findExternalReferenceOnly(String query, LocalDate today, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("today", today);
        StringBuilder search = new StringBuilder();
        appendSearchFilter(search, params, query);

        String sql = """
                WITH current_effective AS (
                    SELECT DISTINCT ON (eep.book_id)
                        eep.book_id,
                        eep.price,
                        eep.currency,
                        eep.valid_from,
                        eep.authority
                    FROM effective_editorial_prices eep
                    WHERE eep.active = TRUE
                      AND eep.valid_from <= :today
                    ORDER BY eep.book_id, eep.valid_from DESC, eep.id DESC
                )
                SELECT
                    b.id AS book_id,
                    b.title,
                    COALESCE(b.isbn_13, b.isbn_10) AS isbn,
                    p.name AS publisher_name,
                    'EXTERNAL_REFERENCE_ONLY'::varchar AS issue_type,
                    ce.price AS current_price,
                    ce.currency,
                    ce.valid_from AS current_valid_from,
                    NULL::date AS conflict_valid_from,
                    NULL::date AS next_valid_from,
                    GREATEST(
                        ce.valid_from,
                        COALESCE(ev.last_evidence_on, ce.valid_from)
                    ) AS last_evidence_on,
                    COUNT(*) OVER() AS total_count
                FROM current_effective ce
                JOIN books b ON b.id = ce.book_id AND b.active = TRUE
                LEFT JOIN publishers p ON p.id = b.publisher_id
                LEFT JOIN LATERAL (
                    SELECT MAX(
                        GREATEST(
                            ep.valid_from,
                            COALESCE(conf.last_confirmed_on, ep.valid_from)
                        )
                    ) AS last_evidence_on
                    FROM editorial_prices ep
                    LEFT JOIN LATERAL (
                        SELECT MAX(c.confirmed_on) AS last_confirmed_on
                        FROM editorial_price_confirmations c
                        WHERE c.editorial_price_id = ep.id
                          AND c.confirmed_on <= :today
                    ) conf ON TRUE
                    WHERE ep.book_id = ce.book_id
                      AND ep.active = TRUE
                      AND ep.valid_from <= :today
                      AND ep.origin = 'MANUAL_EXTERNAL'
                      AND ep.price = ce.price
                      AND UPPER(ep.currency) = UPPER(ce.currency)
                ) ev ON TRUE
                WHERE ce.authority = 'EXTERNAL_REFERENCE'
                """ + search + """
                ORDER BY last_evidence_on ASC, b.title ASC, b.id ASC
                LIMIT :limit OFFSET :offset
                """;

        return queryPage(sql, params, pageable);
    }

    private Page<EditorialPriceHealthIssueResponse> queryPage(
            String sql,
            MapSqlParameterSource params,
            Pageable pageable
    ) {
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        long[] total = {0L};

        List<EditorialPriceHealthIssueResponse> content = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            total[0] = rs.getLong("total_count");
            return mapIssue(rs, rowNum);
        });

        return new PageImpl<>(content, pageable, total[0]);
    }

    private void appendSearchFilter(StringBuilder where, MapSqlParameterSource params, String query) {
        if (query == null || query.isBlank()) return;

        where.append("""
                 AND (
                     LOWER(b.title) LIKE :query
                     OR LOWER(COALESCE(p.name, '')) LIKE :query
                     OR COALESCE(b.isbn_13, b.isbn_10, '') LIKE :rawQuery
                 )
                """);

        String normalized = query.trim();
        params.addValue("query", "%" + normalized.toLowerCase() + "%");
        params.addValue("rawQuery", "%" + normalized + "%");
    }
}