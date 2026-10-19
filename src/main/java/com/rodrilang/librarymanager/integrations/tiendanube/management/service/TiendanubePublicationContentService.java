package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubePublicationContentRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationContentResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TiendanubePublicationContentService {
    private final BookstoreContext bookstoreContext;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProductLinkRepository linkRepository;
    private final TiendanubeClient client;

    public TiendanubePublicationContentResponse get(Long inventoryId) {
        Context context = context(inventoryId);
        return response(context, client.getProduct(context.store().getStoreId(), context.link().getTiendanubeProductId()));
    }

    public TiendanubePublicationContentResponse update(Long inventoryId, TiendanubePublicationContentRequest request) {
        Context context = context(inventoryId);
        Long storeId = context.store().getStoreId();
        Long productId = context.link().getTiendanubeProductId();
        TiendanubeProductResponse original = client.getProduct(storeId, productId);
        String isbn = isbn(context.link());
        String html = buildHtml(request, isbn);
        Map<String, String> localized = new LinkedHashMap<>(original.description() == null ? Map.of() : original.description());
        String locale = localized.containsKey("es") ? "es" : localized.keySet().stream().findFirst().orElse("es");
        localized.put(locale, html);
        client.updateProduct(storeId, productId, new TiendanubeUpdateProductRequest(null, localized));
        return response(context, client.getProduct(storeId, productId));
    }

    private Context context(Long inventoryId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        TiendanubeProductLink link = linkRepository.findWithInventoryBookByInventoryIdAndActiveTrue(inventoryId)
                .filter(l -> l.getInventory().getBookstore().getId().equals(bookstoreId))
                .filter(l -> l.getTiendanubeStoreId().equals(store.getStoreId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no vinculada"));
        return new Context(store, link);
    }

    private TiendanubePublicationContentResponse response(Context context, TiendanubeProductResponse product) {
        String html = product.description() == null ? "" : product.description().getOrDefault("es",
                product.description().values().stream().findFirst().orElse(""));
        return new TiendanubePublicationContentResponse(
                context.link().getInventory().getId(), context.link().getTiendanubeProductId(),
                context.link().getInventory().getBook().getTitle(),
                isbn(context.link()), html, !html.isBlank(),
                context.link().getInventory().getBook().getDescription());
    }

    /** Escapa la entrada para que los textos de librería nunca inyecten marcado arbitrario. */
    private static String isbn(TiendanubeProductLink link) {
        String isbn13 = link.getInventory().getBook().getIsbn13();
        return isbn13 != null && !isbn13.isBlank() ? isbn13 : link.getInventory().getBook().getIsbn10();
    }

    private static String buildHtml(TiendanubePublicationContentRequest input, String isbn) {
        StringBuilder html = new StringBuilder();
        if (!input.synopsis().isBlank()) html.append("<p>").append(escape(input.synopsis())).append("</p>");
        if (!input.commercialMessage().isBlank()) html.append("<p><strong>").append(escape(input.commercialMessage())).append("</strong></p>");
        if (input.includeIsbn() && isbn != null && !isbn.isBlank())
            html.append("<p>ISBN: ").append(escape(isbn)).append("</p>");
        return html.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;").replace("\r\n", "\n")
                .replace("\n", "<br>");
    }

    private record Context(TiendanubeStore store, TiendanubeProductLink link) {}
}
