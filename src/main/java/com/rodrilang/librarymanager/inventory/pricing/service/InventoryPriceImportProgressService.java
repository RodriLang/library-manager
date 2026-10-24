package com.rodrilang.librarymanager.inventory.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;

/** Updates only progress columns, in independent JDBC statements, without flushing preview entities. */
@Service
@RequiredArgsConstructor
public class InventoryPriceImportProgressService {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(Long importId, String phase, int processedRows) {
        jdbc.update("""
                UPDATE inventory_price_imports
                   SET preview_phase = ?, preview_processed_rows = ?, preview_progress_at = ?
                 WHERE id = ? AND status = 'PREVIEW_PROCESSING'
                """, phase, processedRows, Instant.now(), importId);
    }
}
