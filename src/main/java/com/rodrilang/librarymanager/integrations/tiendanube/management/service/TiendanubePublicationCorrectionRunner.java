package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
@RequiredArgsConstructor
public class TiendanubePublicationCorrectionRunner {
    private final JdbcTemplate jdbc;
    private final com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient client;
    private final com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository links;
    @Async
    public void run(Long jobId) {
        jdbc.update("UPDATE tiendanube_publication_correction_jobs SET status='PROCESSING',started_at=now() WHERE id=? AND status='QUEUED'",jobId);
        var job = jdbc.queryForMap("SELECT action,store_id,bookstore_id FROM tiendanube_publication_correction_jobs WHERE id=?",jobId);
        String action = (String) job.get("action");
        Long storeId = ((Number) job.get("store_id")).longValue();
        Long bookstoreId = ((Number) job.get("bookstore_id")).longValue();
        var items=jdbc.query("SELECT id,product_id FROM tiendanube_publication_correction_items WHERE job_id=? AND status='READY' ORDER BY id",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},jobId);
        for(var item:items) {
            try {
                var product = client.getProduct(storeId,item[1]);
                var itemRecord = jdbc.queryForMap("SELECT inventory_id,after_value FROM tiendanube_publication_correction_items WHERE id=?",item[0]);
                Long inventoryId = itemRecord.get("inventory_id") == null ? null : ((Number)itemRecord.get("inventory_id")).longValue();
                String proposal = (String)itemRecord.get("after_value");
                switch(action) {
                    case "IDENTIFIERS" -> {
                        if(inventoryId==null || product.variants()==null) throw new IllegalStateException("No hay inventario vinculado");
                        String isbn = proposal==null?"":proposal.replace("ISBN: ","");
                        if(isbn.isBlank()) throw new IllegalStateException("ISBN no confirmado");
                        var variant = product.variants().stream().filter(v -> links.findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(storeId,v.id())
                             .filter(l -> l.getInventory().getId().equals(inventoryId) && l.getInventory().getBookstore().getId().equals(bookstoreId) &&
                                   l.getTiendanubeProductId().equals(item[1]) && (isbn.equals(l.getInventory().getBook().getIsbn13()) || isbn.equals(l.getInventory().getBook().getIsbn10())))
                             .isPresent()).findFirst().orElseThrow(() -> new IllegalStateException("El vínculo o ISBN cambió"));
                        if((variant.sku()!=null && !variant.sku().isBlank() && !isbn.equals(variant.sku().trim())) ||
                           (variant.barcode()!=null && !variant.barcode().isBlank() && !isbn.equals(variant.barcode().trim())))
                           throw new IllegalStateException("Identificadores incompatibles");
                        client.updateVariant(storeId,item[1],variant.id(),new com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest(
                             isbn,isbn,null,null,null,null,null,null,null));
                    }
                    case "DESCRIPTION" -> {
                        boolean hasText = product.description()!=null && product.description().values().stream().anyMatch(v -> v!=null && !v.replaceAll("(?s)<[^>]*>","").replace("&nbsp;"," ").isBlank());
                        if(hasText || proposal==null || proposal.isBlank()) throw new IllegalStateException("La descripción cambió o la propuesta está vacía");
                        var localized = new java.util.LinkedHashMap<String,String>(product.description()==null?java.util.Map.of():product.description());
                        String locale=localized.containsKey("es")?"es":localized.keySet().stream().findFirst().orElse("es");
                        localized.put(locale,proposal);
                        client.updateProduct(storeId,item[1],new com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateProductRequest(null,localized));
                    }
                    case "COVER" -> {
                        if(product.images()!=null && product.images().stream().anyMatch(i -> i.src()!=null && !i.src().isBlank()))
                            throw new IllegalStateException("La publicación ya tiene portada");
                        if(proposal==null || !proposal.matches("(?i)^https://[^\\s]+$")) throw new IllegalStateException("No hay URL HTTPS validada");
                        client.createProductImage(storeId,item[1],new com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest(proposal,1));
                    }
                }
                jdbc.update("UPDATE tiendanube_publication_correction_items SET status='COMPLETED' WHERE id=?",item[0]);
            } catch(Exception ex) {
                jdbc.update("UPDATE tiendanube_publication_correction_items SET status='FAILED',reason=? WHERE id=?", "No se pudo aplicar: " + (ex.getMessage()==null?"error remoto":ex.getMessage()).substring(0,Math.min(400,("No se pudo aplicar: " + (ex.getMessage()==null?"error remoto":ex.getMessage())).length())),item[0]);
            }
        }
        jdbc.update("UPDATE tiendanube_publication_correction_jobs SET status='COMPLETED',finished_at=now() WHERE id=?",jobId);
    }
}
