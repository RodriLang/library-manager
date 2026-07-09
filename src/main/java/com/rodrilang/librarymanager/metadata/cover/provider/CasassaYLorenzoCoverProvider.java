package com.rodrilang.librarymanager.metadata.cover.provider;

import com.rodrilang.librarymanager.metadata.cover.CoverCandidate;
import com.rodrilang.librarymanager.model.Book;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Order(1)
@Component
public class CasassaYLorenzoCoverProvider extends DirectUrlCoverProvider {

    private static final String COVER_URL =
            "https://contentv2.tap-commerce.com/cover/large/%s_1.jpg";

    public CasassaYLorenzoCoverProvider(
            @Qualifier("coverProviderRestClient")
            RestClient restClient) {
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
                90
        );
    }

    @Override
    public String name() {
        return "Casassa y Lorenzo";
    }
}