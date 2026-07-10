package com.rodrilang.librarymanager.metadata.cover.provider;

import com.rodrilang.librarymanager.metadata.cover.CoverCandidate;
import com.rodrilang.librarymanager.model.Book;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
@Order(4)
public class AbeBooksCoverProvider extends DirectUrlCoverProvider {

    private static final String COVER_URL =
            "https://pictures.abebooks.com/isbn/%s-fr.jpg";

    public AbeBooksCoverProvider(
            @Qualifier("coverProviderRestClient") RestClient restClient
    ) {
        super(restClient);
    }

    @Override
    public Optional<CoverCandidate> findCover(Book book) {

        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            return Optional.empty();
        }

        String url = COVER_URL.formatted(book.getIsbn().trim());

        return buildCandidate(
                url,
                name(),
                book.getTitle(),
                "image/jpeg",
                70
        );
    }

    @Override
    public String name() {
        return "AbeBooks";
    }
}