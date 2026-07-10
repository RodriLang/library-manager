package com.rodrilang.librarymanager.metadata.cover.provider;

import com.rodrilang.librarymanager.metadata.cover.CoverCandidate;
import com.rodrilang.librarymanager.model.Book;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Order(2)
@Component
public class LibrosRefCoverProvider implements CoverProvider {

    private static final String COVER_URL = "https://apiultragestion.com.ar/media/tapas/%s.jpg";

    private final RestClient restClient;

    public LibrosRefCoverProvider(
            @Qualifier("coverProviderRestClient")
            RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<CoverCandidate> findCover(Book book) {

        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            return Optional.empty();
        }

        String url = COVER_URL.formatted(book.getIsbn());

        if (!exists(url)) {
            return Optional.empty();
        }

        return Optional.of(new CoverCandidate(
                url,
                book.getTitle(),
                "LibrosRef",
                "image/jpeg",
                60 // prioridad menor por el borde blanco
        ));
    }

    @Override
    public String name() {
        return "LibrosRef";
    }

    private boolean exists(String url) {
        try {
            return restClient.method(HttpMethod.HEAD)
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception ex) {
            return false;
        }
    }
}
