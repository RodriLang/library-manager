package com.rodrilang.librarymanager.inventory.pricing.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class InventoryPriceImportProviderRowJdbcRepository {
    private static final int INSERT_BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertAll(long importId, List<StagedRow> rows) {
        if (rows.isEmpty()) return;
        Timestamp createdAt = Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate("""
                INSERT INTO inventory_price_import_provider_rows
                    (import_id, row_number, isbn, title, author, publisher, incoming_price, external_code, book_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (import_id, row_number) DO UPDATE SET
                    isbn = EXCLUDED.isbn,
                    title = EXCLUDED.title,
                    author = EXCLUDED.author,
                    publisher = EXCLUDED.publisher,
                    incoming_price = EXCLUDED.incoming_price,
                    external_code = EXCLUDED.external_code,
                    book_id = COALESCE(EXCLUDED.book_id, inventory_price_import_provider_rows.book_id)
                """, rows, INSERT_BATCH_SIZE, (ps, row) -> bindInsert(ps, importId, row, createdAt));
    }

    public List<StoredRow> findBatch(long importId, long afterId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, row_number, isbn, title, author, publisher, incoming_price, external_code, book_id
                FROM inventory_price_import_provider_rows
                WHERE import_id = ? AND id > ?
                ORDER BY id
                LIMIT ?
                """, (rs, n) -> new StoredRow(
                rs.getLong("id"), rs.getInt("row_number"), rs.getString("isbn"), rs.getString("title"),
                rs.getString("author"), rs.getString("publisher"), rs.getBigDecimal("incoming_price"),
                rs.getString("external_code"), (Long) rs.getObject("book_id")
        ), importId, afterId, limit);
    }

    @Transactional
    public void updateResolvedBooks(List<ResolvedBook> rows) {
        if (rows.isEmpty()) return;
        jdbcTemplate.batchUpdate(
                "UPDATE inventory_price_import_provider_rows SET book_id = ? WHERE id = ?",
                rows, rows.size(), (ps, row) -> { ps.setLong(1, row.bookId()); ps.setLong(2, row.stagingId()); }
        );
    }

    public int countByImportId(long importId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_price_import_provider_rows WHERE import_id = ?", Integer.class, importId);
        return value == null ? 0 : value;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteByImportId(long importId) {
        jdbcTemplate.update("DELETE FROM inventory_price_import_provider_rows WHERE import_id = ?", importId);
    }

    private void bindInsert(PreparedStatement ps, long importId, StagedRow row, Timestamp createdAt) throws java.sql.SQLException {
        ps.setLong(1, importId);
        ps.setInt(2, row.rowNumber());
        ps.setString(3, row.isbn());
        ps.setString(4, row.title());
        ps.setString(5, row.author());
        ps.setString(6, row.publisher());
        ps.setBigDecimal(7, row.incomingPrice());
        ps.setString(8, row.externalCode());
        if (row.bookId() == null) ps.setObject(9, null); else ps.setLong(9, row.bookId());
        ps.setTimestamp(10, createdAt);
    }

    public record StagedRow(int rowNumber, String isbn, String title, String author, String publisher,
                            BigDecimal incomingPrice, String externalCode, Long bookId) {}
    public record StoredRow(long id, int rowNumber, String isbn, String title, String author, String publisher,
                            BigDecimal incomingPrice, String externalCode, Long bookId) {}
    public record ResolvedBook(long stagingId, long bookId) {}
}
