package com.rodrilang.librarymanager.metadata.cover.provider;

import com.rodrilang.librarymanager.metadata.cover.CoverCandidate;
import com.rodrilang.librarymanager.model.Book;
import org.springframework.web.client.RestClient;

import java.util.Optional;

public abstract class PrefixImageCoverProvider extends DirectUrlCoverProvider {

    protected PrefixImageCoverProvider(RestClient restClient) {
        super(restClient);
    }

    @Override
    public Optional<CoverCandidate> findCover(Book book) {
        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            return Optional.empty();
        }

        String isbn = book.getIsbn().trim();

        if (isbn.length() < 7) {
            return Optional.empty();
        }

        String prefix = isbn.substring(0, 7);
        String url = buildUrl(prefix, isbn);

        return buildCandidate(
                url,
                name(),
                book.getTitle(),
                mime(),
                score()
        );
    }

    protected abstract String buildUrl(String prefix, String isbn);

    protected abstract String mime();

    protected abstract int score();
}