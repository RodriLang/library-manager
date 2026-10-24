package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubePublicationTemplateRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationTemplateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TiendanubePublicationTemplateService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public TiendanubePublicationTemplateResponse get(Long bookstoreId) {
        List<TiendanubePublicationTemplateResponse> found = jdbc.query(
            "SELECT enabled, commercial_message, include_isbn FROM tiendanube_publication_templates WHERE bookstore_id = ?",
            (rs, row) -> new TiendanubePublicationTemplateResponse(rs.getBoolean(1), rs.getString(2), rs.getBoolean(3)), bookstoreId);
        return found.isEmpty() ? new TiendanubePublicationTemplateResponse(false, "", true) : found.getFirst();
    }

    @Transactional
    public TiendanubePublicationTemplateResponse save(Long bookstoreId, TiendanubePublicationTemplateRequest request) {
        jdbc.update("""
            INSERT INTO tiendanube_publication_templates (bookstore_id, enabled, commercial_message, include_isbn)
            VALUES (?, ?, ?, ?) ON CONFLICT (bookstore_id) DO UPDATE SET
            enabled=EXCLUDED.enabled, commercial_message=EXCLUDED.commercial_message,
            include_isbn=EXCLUDED.include_isbn, updated_at=now()
            """, bookstoreId, request.enabled(), request.commercialMessage(), request.includeIsbn());
        return get(bookstoreId);
    }

    /** Sólo texto controlado por la librería; nunca se inserta HTML sin escapar. */
    public String render(String synopsis, String isbn, TiendanubePublicationTemplateResponse template) {
        StringBuilder html = new StringBuilder();
        if (synopsis != null && !synopsis.isBlank())
            html.append("<p>").append(escape(synopsis)).append("</p>");
        if (template.commercialMessage() != null && !template.commercialMessage().isBlank())
            html.append("<p><strong>").append(escape(template.commercialMessage())).append("</strong></p>");
        if (template.includeIsbn() && isbn != null && !isbn.isBlank())
            html.append("<p>ISBN: ").append(escape(isbn)).append("</p>");
        return html.toString();
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;")
                .replace("\r\n", "\n").replace("\n", "<br>");
    }
}
