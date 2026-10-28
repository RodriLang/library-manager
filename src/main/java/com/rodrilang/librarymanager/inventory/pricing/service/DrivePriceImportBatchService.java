package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Durable batch manifest. Files are downloaded only when requested, never retained as a batch in RAM. */
@Service
@RequiredArgsConstructor
public class DrivePriceImportBatchService {
    private final JdbcTemplate jdbc;
    private final BookstoreContext bookstore;
    private final GoogleDrivePriceListService drive;

    public record Item(long id, String driveFileId, String filename, long size, int position,
                       String status, Long importId, String errorMessage) {}
    public record Batch(long id, String folderUrl, List<Item> items) {}

    @Transactional
    public Batch create(String url, List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty() || fileIds.size() > 300 || new HashSet<>(fileIds).size() != fileIds.size())
            throw new BusinessException("Seleccioná entre 1 y 300 archivos diferentes.");
        var available = drive.explore(url).files();
        var byId = new HashMap<String, GoogleDrivePriceListService.DriveFile>();
        available.forEach(f -> byId.put(f.id(), f));
        for (String id : fileIds) if (!byId.containsKey(id))
            throw new BusinessException("Uno de los archivos seleccionados ya no está disponible en Drive.");
        Long batchId = jdbc.queryForObject("INSERT INTO drive_price_import_batches (bookstore_id, created_by_user_id, folder_url) VALUES (?, ?, ?) RETURNING id",
                Long.class, bookstore.getCurrentBookstoreId(), bookstore.getCurrentUserId(), url);
        int i = 0;
        for (String id : fileIds) {
            var f = byId.get(id);
            jdbc.update("INSERT INTO drive_price_import_batch_items (batch_id, drive_file_id, filename, file_size, position) VALUES (?, ?, ?, ?, ?)",
                    batchId, id, f.name(), f.size(), i++);
        }
        return get(batchId);
    }

    public Batch get(long id) {
        Long tenant = bookstore.getCurrentBookstoreId();
        var folderUrls = jdbc.query("SELECT folder_url FROM drive_price_import_batches WHERE id=? AND bookstore_id=?",
                (rs, rowNum) -> rs.getString(1), id, tenant);
        if (folderUrls.isEmpty()) throw new BusinessException("No se encontró el lote para esta librería.");
        List<Item> items = jdbc.query("SELECT id, drive_file_id, filename, file_size, position, status, import_id, error_message FROM drive_price_import_batch_items WHERE batch_id=? ORDER BY position",
                (rs, row) -> new Item(rs.getLong("id"), rs.getString("drive_file_id"), rs.getString("filename"), rs.getLong("file_size"), rs.getInt("position"),
                        rs.getString("status"), (Long) rs.getObject("import_id"), rs.getString("error_message")), id);
        return new Batch(id, folderUrls.get(0), items);
    }

    @Transactional
    public Batch update(long batchId, long itemId, String status, Long importId, String error) {
        get(batchId); // tenant isolation
        if (!Set.of("PENDING", "IN_REVIEW", "COMPLETED", "ERROR", "SKIPPED").contains(status))
            throw new BusinessException("Estado de lista inválido.");
        if (importId != null) {
            Integer owned = jdbc.queryForObject("SELECT count(*) FROM inventory_price_imports WHERE id=? AND bookstore_id=?",
                    Integer.class, importId, bookstore.getCurrentBookstoreId());
            if (owned == null || owned == 0) throw new BusinessException("La importación no pertenece a esta librería.");
        }
        if (error != null && error.length() > 1000) error = error.substring(0, 1000);
        int updated = jdbc.update("UPDATE drive_price_import_batch_items SET status=?, import_id=COALESCE(?,import_id), error_message=? WHERE id=? AND batch_id=?",
                status, importId, error, itemId, batchId);
        if (updated != 1) throw new BusinessException("No se encontró la lista del lote.");
        return get(batchId);
    }
}
