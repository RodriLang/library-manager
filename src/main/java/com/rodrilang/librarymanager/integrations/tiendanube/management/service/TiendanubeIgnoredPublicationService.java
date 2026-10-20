package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Local and reversible exclusion; it never affects remote products. */
@Service
@RequiredArgsConstructor
public class TiendanubeIgnoredPublicationService {
    private final BookstoreContext bookstoreContext;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Long> list() {
        return jdbc.queryForList("SELECT product_id FROM tiendanube_ignored_publications WHERE bookstore_id = ? ORDER BY ignored_at DESC",
                Long.class, bookstoreContext.getCurrentBookstoreId());
    }

    @Transactional
    public void ignore(long productId) {
        requireValidProduct(productId);
        jdbc.update("INSERT INTO tiendanube_ignored_publications (bookstore_id, product_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                bookstoreContext.getCurrentBookstoreId(), productId);
    }

    @Transactional
    public void restore(long productId) {
        requireValidProduct(productId);
        jdbc.update("DELETE FROM tiendanube_ignored_publications WHERE bookstore_id = ? AND product_id = ?",
                bookstoreContext.getCurrentBookstoreId(), productId);
    }

    private void requireValidProduct(long productId) {
        if (productId <= 0) throw new IllegalArgumentException("productId must be positive");
    }
}
