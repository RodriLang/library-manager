package com.rodrilang.librarymanager.admin.catalog.controller;

import com.rodrilang.librarymanager.admin.catalog.dto.AdminCatalogBookResponse;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin/catalog/books")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public PageResponse<AdminCatalogBookResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String issue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        List<Object> args = new ArrayList<>();
        String where = buildWhere(q, issue, args);

        Long total = jdbc.queryForObject("select count(*) from books b where b.active=true " + where, Long.class, args.toArray());
        List<Object> dataArgs = new ArrayList<>(args);
        dataArgs.add(safeSize);
        dataArgs.add(safePage * safeSize);

        String sql = """
                select b.id, b.title, coalesce(b.isbn_13,b.isbn_10) isbn, p.name publisher, b.cover_url, b.updated_at,
                       coalesce((select string_agg(a.name, ', ' order by a.name) from book_authors ba join authors a on a.id=ba.author_id where ba.book_id=b.id),'') authors,
                       (b.cover_url is null or btrim(b.cover_url)='') missing_cover,
                       (b.publisher_id is null) missing_publisher,
                       (b.isbn_13 is null and b.isbn_10 is null) missing_isbn,
                       (not exists(select 1 from book_authors ba2 where ba2.book_id=b.id)) missing_author
                from books b
                left join publishers p on p.id=b.publisher_id
                where b.active=true
                """ + where + " order by b.title_sort, b.id limit ? offset ?";

        List<AdminCatalogBookResponse> content = jdbc.query(sql, (rs, rowNum) -> {
            Long id = rs.getLong("id");
            List<String> issues = new ArrayList<>();
            if (rs.getBoolean("missing_cover")) issues.add("MISSING_COVER");
            if (rs.getBoolean("missing_publisher")) issues.add("MISSING_PUBLISHER");
            if (rs.getBoolean("missing_isbn")) issues.add("MISSING_ISBN");
            if (rs.getBoolean("missing_author")) issues.add("MISSING_AUTHOR");
            Timestamp updated = rs.getTimestamp("updated_at");
            return new AdminCatalogBookResponse(id, rs.getString("title"), rs.getString("isbn"), rs.getString("publisher"),
                    rs.getString("authors"), rs.getString("cover_url"), updated == null ? null : updated.toInstant(), issues);
        }, dataArgs.toArray());

        long count = total == null ? 0 : total;
        int pages = (int)Math.ceil(count / (double)safeSize);
        return PageResponse.<AdminCatalogBookResponse>builder()
                .content(content).pageNumber(safePage).pageSize(safeSize).totalElements(count).totalPages(pages)
                .first(safePage==0).last(pages==0 || safePage>=pages-1).empty(content.isEmpty()).build();
    }

    private String buildWhere(String q, String issue, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        if (q != null && !q.isBlank()) {
            sql.append(" and (lower(b.title) like lower(?) or b.isbn_13 like ? or b.isbn_10 like ?)");
            String pattern = "%" + q.trim() + "%";
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        if (issue != null && !issue.isBlank()) {
            switch (issue) {
                case "MISSING_COVER" -> sql.append(" and (b.cover_url is null or btrim(b.cover_url)='')");
                case "MISSING_PUBLISHER" -> sql.append(" and b.publisher_id is null");
                case "MISSING_ISBN" -> sql.append(" and b.isbn_13 is null and b.isbn_10 is null");
                case "MISSING_AUTHOR" -> sql.append(" and not exists(select 1 from book_authors ba where ba.book_id=b.id)");
                case "MISSING_DESCRIPTION" -> sql.append(" and (b.description is null or btrim(b.description)='')");
                case "MISSING_LANGUAGE" -> sql.append(" and (b.language is null or btrim(b.language)='')");
                case "MISSING_PAGES" -> sql.append(" and b.page_count is null");
                case "MISSING_PUBLICATION_YEAR" -> sql.append(" and b.publication_year is null");
                default -> { }
            }
        }
        return sql.toString();
    }
}
