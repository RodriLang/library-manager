package com.rodrilang.librarymanager.admin.catalog.controller;

import com.rodrilang.librarymanager.admin.catalog.dto.AdminCatalogBookResponse;
import com.rodrilang.librarymanager.admin.catalog.dto.AdminCatalogUsageFilter;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/catalog/books")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private static final String ACTIVE_INVENTORY_EXISTS = """
            exists (
                select 1
                from inventory i
                where i.book_id = b.id
                  and i.active = true
            )
            """;

    private final JdbcTemplate jdbc;

    @GetMapping
    public PageResponse<AdminCatalogBookResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String issue,
            @RequestParam(required = false) AdminCatalogUsageFilter usage,
            @RequestParam(required = false) Long publisherId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        List<Object> args = new ArrayList<>();
        String where = buildWhere(q, issue, usage, publisherId, args);

        Long total = jdbc.queryForObject(
                "select count(*) from books b where b.active=true " + where,
                Long.class,
                args.toArray()
        );

        List<Object> dataArgs = new ArrayList<>(args);
        dataArgs.add(safeSize);
        dataArgs.add(safePage * safeSize);

        String sql = """
                select b.id,
                       b.title,
                       coalesce(b.isbn_13, b.isbn_10) isbn,
                       p.name publisher,
                       b.cover_url,
                       b.updated_at,
                       coalesce((
                           select string_agg(a.name, ', ' order by a.name)
                           from book_authors ba
                           join authors a on a.id = ba.author_id
                           where ba.book_id = b.id
                       ), '') authors,
                       (b.cover_url is null or btrim(b.cover_url) = '') missing_cover,
                       (b.publisher_id is null) missing_publisher,
                       (b.isbn_13 is null and b.isbn_10 is null) missing_isbn,
                       (not exists(select 1 from book_authors ba2 where ba2.book_id = b.id)) missing_author,
                       (b.description is null or btrim(b.description) = '') missing_description,
                       (b.language is null or btrim(b.language) = '') missing_language,
                       (b.page_count is null) missing_pages,
                       (b.publication_year is null) missing_publication_year,
                       %s as in_inventory
                from books b
                left join publishers p on p.id = b.publisher_id
                where b.active = true
                %s
                order by b.title_sort, b.id
                limit ? offset ?
                """.formatted(ACTIVE_INVENTORY_EXISTS, where);

        List<AdminCatalogBookResponse> content = jdbc.query(sql, (rs, rowNum) -> {
            Long id = rs.getLong("id");
            List<String> issues = new ArrayList<>();
            if (rs.getBoolean("missing_cover")) issues.add("MISSING_COVER");
            if (rs.getBoolean("missing_author")) issues.add("MISSING_AUTHOR");
            if (rs.getBoolean("missing_publisher")) issues.add("MISSING_PUBLISHER");
            if (rs.getBoolean("missing_isbn")) issues.add("MISSING_ISBN");
            if (rs.getBoolean("missing_description")) issues.add("MISSING_DESCRIPTION");
            if (rs.getBoolean("missing_language")) issues.add("MISSING_LANGUAGE");
            if (rs.getBoolean("missing_pages")) issues.add("MISSING_PAGES");
            if (rs.getBoolean("missing_publication_year")) issues.add("MISSING_PUBLICATION_YEAR");

            Timestamp updated = rs.getTimestamp("updated_at");
            return new AdminCatalogBookResponse(
                    id,
                    rs.getString("title"),
                    rs.getString("isbn"),
                    rs.getString("publisher"),
                    rs.getString("authors"),
                    rs.getString("cover_url"),
                    updated == null ? null : updated.toInstant(),
                    issues,
                    rs.getBoolean("in_inventory")
            );
        }, dataArgs.toArray());

        long count = total == null ? 0 : total;
        int pages = (int) Math.ceil(count / (double) safeSize);
        return PageResponse.<AdminCatalogBookResponse>builder()
                .content(content)
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalElements(count)
                .totalPages(pages)
                .first(safePage == 0)
                .last(pages == 0 || safePage >= pages - 1)
                .empty(content.isEmpty())
                .build();
    }

    private String buildWhere(
            String q,
            String issue,
            AdminCatalogUsageFilter usage,
            Long publisherId,
            List<Object> args
    ) {
        StringBuilder sql = new StringBuilder();

        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim() + "%";
            sql.append("""
                     and (
                         b.title ilike ?
                         or b.isbn_13 ilike ?
                         or b.isbn_10 ilike ?
                         or exists (
                             select 1
                             from book_authors search_ba
                             join authors search_a on search_a.id = search_ba.author_id
                             where search_ba.book_id = b.id
                               and search_a.name ilike ?
                         )
                         or exists (
                             select 1
                             from publishers search_p
                             where search_p.id = b.publisher_id
                               and search_p.name ilike ?
                         )
                     )
                    """);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }

        appendIssueFilter(sql, issue);

        if (usage != null) {
            switch (usage) {
                case IN_INVENTORY -> sql.append(" and ").append(ACTIVE_INVENTORY_EXISTS);
                case NOT_IN_INVENTORY -> sql.append(" and not (").append(ACTIVE_INVENTORY_EXISTS).append(")");
            }
        }

        if (publisherId != null) {
            sql.append(" and b.publisher_id = ?");
            args.add(publisherId);
        }

        return sql.toString();
    }

    private void appendIssueFilter(StringBuilder sql, String issue) {
        if (issue == null || issue.isBlank()) {
            return;
        }

        switch (issue.trim().toUpperCase()) {
            case "MISSING_COVER" -> sql.append(" and (b.cover_url is null or btrim(b.cover_url) = '')");
            case "MISSING_AUTHOR" -> sql.append(" and not exists(select 1 from book_authors ba where ba.book_id = b.id)");
            case "MISSING_PUBLISHER" -> sql.append(" and b.publisher_id is null");
            case "MISSING_ISBN" -> sql.append(" and b.isbn_13 is null and b.isbn_10 is null");
            case "MISSING_DESCRIPTION" -> sql.append(" and (b.description is null or btrim(b.description) = '')");
            case "MISSING_LANGUAGE" -> sql.append(" and (b.language is null or btrim(b.language) = '')");
            case "MISSING_PAGES" -> sql.append(" and b.page_count is null");
            case "MISSING_PUBLICATION_YEAR" -> sql.append(" and b.publication_year is null");
            default -> {
                // Unknown issue values are intentionally ignored for backwards compatibility.
            }
        }
    }
}
