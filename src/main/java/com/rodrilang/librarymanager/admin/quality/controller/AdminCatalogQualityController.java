package com.rodrilang.librarymanager.admin.quality.controller;

import com.rodrilang.librarymanager.admin.quality.dto.CatalogQualityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/catalog/quality")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogQualityController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public CatalogQualityResponse quality() {
        long total = count("select count(*) from books where active=true");
        return new CatalogQualityResponse(total, List.of(
                issue("MISSING_COVER", "Sin portada", "cover_url is null or btrim(cover_url) = ''"),
                issue("MISSING_AUTHOR", "Sin autor", "not exists (select 1 from book_authors ba where ba.book_id=b.id)"),
                issue("MISSING_PUBLISHER", "Sin editorial", "publisher_id is null"),
                issue("MISSING_ISBN", "Sin ISBN", "isbn_13 is null and isbn_10 is null"),
                issue("MISSING_DESCRIPTION", "Sin descripción", "description is null or btrim(description) = ''"),
                issue("MISSING_LANGUAGE", "Sin idioma", "language is null or btrim(language) = ''"),
                issue("MISSING_PAGES", "Sin cantidad de páginas", "page_count is null"),
                issue("MISSING_PUBLICATION_YEAR", "Sin año de publicación", "publication_year is null")
        ));
    }

    private CatalogQualityResponse.CatalogIssueResponse issue(String code, String label, String condition) {
        return new CatalogQualityResponse.CatalogIssueResponse(code, label, count("select count(*) from books b where b.active=true and ("+condition+")"));
    }
    private long count(String sql) { Long v=jdbc.queryForObject(sql, Long.class); return v==null?0:v; }
}
