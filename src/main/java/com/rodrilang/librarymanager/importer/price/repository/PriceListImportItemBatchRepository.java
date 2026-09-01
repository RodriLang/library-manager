package com.rodrilang.librarymanager.importer.price.repository;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListImportItemInsertRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PriceListImportItemBatchRepository {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_SQL = """
            INSERT INTO price_list_import_items (
                job_id,
                book_id,
                editorial_price_id,
                imported_price,
                previous_price,
                operation,
                price_change
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (job_id, book_id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public void insertBatch(
            Long jobId,
            List<PriceListImportItemInsertRow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                rows,
                BATCH_SIZE,
                (statement, row) -> {
                    statement.setLong(1, jobId);
                    statement.setLong(2, row.bookId());
                    statement.setLong(3, row.editorialPriceId());
                    statement.setBigDecimal(4, row.importedPrice());

                    if (row.previousPrice() == null) {
                        statement.setNull(5, Types.NUMERIC);
                    } else {
                        statement.setBigDecimal(5, row.previousPrice());
                    }

                    statement.setString(6, row.operation().name());
                    statement.setString(7, row.priceChange().name());
                }
        );
    }
}