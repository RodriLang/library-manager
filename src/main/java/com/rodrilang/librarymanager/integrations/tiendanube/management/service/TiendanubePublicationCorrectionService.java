package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TiendanubePublicationCorrectionService {
    private final JdbcTemplate jdbc;
    private final BookstoreContext bookstoreContext;
    private final TiendanubeStoreRepository stores;
    private final TiendanubePublicationContentService contents;
    private final TiendanubePublicationTemplateService templates;
    private final TiendanubePublicationCorrectionRunner runner;

    public record Request(String action, List<Long> productIds) {}
    public record Item(Long productId, String title, String status, String reason, String beforeValue, String afterValue) {}
    public record Job(Long id, String action, String status, List<Item> items) {}

    public Job preview(Request request) {
        if (request == null || request.productIds() == null || request.productIds().isEmpty() || request.productIds().size() > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná entre 1 y 50 publicaciones");
        if (!Set.of("IDENTIFIERS", "DESCRIPTION", "COVER").contains(request.action()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción de corrección inválida");
        Long bookstore = bookstoreContext.getCurrentBookstoreId();
        TiendanubeStore store = stores.findByBookstoreIdAndActiveTrue(bookstore)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        Long id = jdbc.queryForObject("INSERT INTO tiendanube_publication_correction_jobs(bookstore_id,store_id,action) VALUES(?,?,?) RETURNING id", Long.class, bookstore, store.getStoreId(), request.action());
        for (Long productId : new LinkedHashSet<>(request.productIds())) {
            if (productId == null || productId < 1) continue;
            String title = "Producto #" + productId, status = "READY", reason = null, before = "", after = "";
            Long inventoryId = null;
            try {
                var p = contents.getRemote(productId);
                title = p.title(); inventoryId = p.inventoryId();
                switch (request.action()) {
                    case "IDENTIFIERS" -> {
                        before = "SKU: " + (p.variant()==null?"":p.variant().sku()) + " / Barcode: " + (p.variant()==null?"":p.variant().barcode());
                        after = "ISBN: " + p.isbn();
                        if (inventoryId == null || p.isbn() == null || p.isbn().isBlank()) {status="SKIPPED"; reason="Falta vínculo o ISBN confirmado";}
                        else if (p.variant() == null) {status="SKIPPED"; reason="No hay variante vinculada";}
                        else if ((p.variant().sku()!=null && !p.variant().sku().isBlank() && !p.isbn().equals(p.variant().sku().trim())) ||
                            (p.variant().barcode()!=null && !p.variant().barcode().isBlank() && !p.isbn().equals(p.variant().barcode().trim()))) {status="SKIPPED"; reason="Hay identificadores diferentes: requiere revisión";}
                        else if (p.isbn().equals(p.variant().sku()) && p.isbn().equals(p.variant().barcode())) {status="SKIPPED"; reason="Los identificadores ya están completos";}
                    }
                    case "DESCRIPTION" -> {
                        before = p.existingHtml();
                        if (p.hasDescription()) {status="SKIPPED"; reason="La descripción existente se conserva";}
                        else if (inventoryId == null || p.anaquelSynopsis()==null || p.anaquelSynopsis().isBlank()) {status="SKIPPED"; reason="No hay sinopsis confirmada de Anaquel";}
                        else after = templates.render(p.anaquelSynopsis(), p.isbn(), templates.get(bookstore));
                    }
                    case "COVER" -> {
                        before = p.remoteCoverUrl(); after = p.anaquelCoverUrl();
                        if (p.remoteCoverUrl()!=null && !p.remoteCoverUrl().isBlank()) {status="SKIPPED"; reason="Ya tiene portada";}
                        else if (inventoryId==null || after==null || !after.matches("(?i)^https://[^\\s]+$")) {status="SKIPPED";reason="No hay portada HTTPS de Anaquel disponible";}
                    }
                }
            } catch (Exception ex) {status="SKIPPED"; reason="No se pudo consultar o validar el producto";}
            jdbc.update("INSERT INTO tiendanube_publication_correction_items(job_id,product_id,title,status,reason,before_value,after_value,inventory_id) VALUES (?,?,?,?,?,?,?,?)", id,productId,title,status,reason,before,after,inventoryId);
        }
        return get(id);
    }
    public Job get(Long id) {
        Long bookstore = bookstoreContext.getCurrentBookstoreId();
        var rows = jdbc.query("SELECT action,status FROM tiendanube_publication_correction_jobs WHERE id=? AND bookstore_id=?", (rs,n)->new String[]{rs.getString(1),rs.getString(2)},id,bookstore);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Operación no encontrada");
        List<Item> items=jdbc.query("SELECT product_id,title,status,reason,before_value,after_value FROM tiendanube_publication_correction_items WHERE job_id=? ORDER BY id", (rs,n)->new Item(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)),id);
        return new Job(id,rows.getFirst()[0],rows.getFirst()[1],items);
    }
    public Job execute(Long id) {
        Long bookstore=bookstoreContext.getCurrentBookstoreId();
        int updated=jdbc.update("UPDATE tiendanube_publication_correction_jobs SET status='QUEUED' WHERE id=? AND bookstore_id=? AND status='PREVIEW'",id,bookstore);
        if(updated!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"La vista previa ya se ejecutó o no existe");
        runner.run(id);
        return get(id);
    }
}
