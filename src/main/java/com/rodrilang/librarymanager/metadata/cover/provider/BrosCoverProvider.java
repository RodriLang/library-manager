package com.rodrilang.librarymanager.metadata.cover.provider;

import com.rodrilang.librarymanager.metadata.cover.CoverCandidate;
import com.rodrilang.librarymanager.model.Book;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Order(3)
@Component
public class BrosCoverProvider extends DirectUrlCoverProvider {

    public BrosCoverProvider(
            @Qualifier("coverProviderRestClient") RestClient restClient
    ) {
        super(restClient);
    }

    @Override
    public Optional<CoverCandidate> findCover(Book book) {
        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            return Optional.empty();
        }

        String isbn = book.getIsbn().trim();

        String url = "https://imagenesal.bros.me/%s.jpg".formatted(isbn);

        return buildCandidate(
                url,
                name(),
                book.getTitle(),
                "image/jpeg",
                68
        );
    }

    @Override
    public String name() {
        return "BROS";
    }
}