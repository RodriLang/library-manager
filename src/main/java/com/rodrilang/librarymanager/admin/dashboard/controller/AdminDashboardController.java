package com.rodrilang.librarymanager.admin.dashboard.controller;

import com.rodrilang.librarymanager.admin.dashboard.dto.AdminDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public AdminDashboardResponse dashboard() {
        return new AdminDashboardResponse(
                count("select count(*) from books where active = true"),
                count("select count(*) from bookstores"),
                count("select count(*) from users"),
                count("select count(*) from providers"),
                count("select count(*) from books where active = true and (cover_url is null or btrim(cover_url) = '')"),
                count("select count(*) from books b where b.active = true and not exists (select 1 from book_authors ba where ba.book_id=b.id)"),
                count("select count(*) from books where active = true and publisher_id is null"),
                count("select count(*) from books where active = true and isbn_13 is null and isbn_10 is null"),
                count("""
                    select count(*) from books b where b.active=true and (
                      b.cover_url is null or b.publisher_id is null or b.isbn_13 is null and b.isbn_10 is null
                      or b.description is null or b.language is null or b.page_count is null or b.publication_year is null
                      or not exists (select 1 from book_authors ba where ba.book_id=b.id)
                    )
                """)
        );
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
