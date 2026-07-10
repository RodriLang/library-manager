package com.rodrilang.librarymanager.metadata.cover.provider;

import com.rodrilang.librarymanager.metadata.cover.CoverCandidate;
import com.rodrilang.librarymanager.model.Book;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

@Order(30)
@Component
public class FceCoverProvider extends DirectUrlCoverProvider {

    private static final String DIRECT_COVER_URL =
            "https://fce.com.ar/wp-content/uploads/2026/06/%s.webp";

    private static final List<String> CATEGORIES = List.of(
            "literatura",
            "historia",
            "filosofia",
            "sociologia",
            "politica",
            "economia",
            "educacion",
            "infantil-y-juvenil",
            "ciencia"
    );

    public FceCoverProvider(
            @Qualifier("coverProviderRestClient")
            RestClient restClient) {
        super(restClient);
    }

    @Override
    public Optional<CoverCandidate> findCover(Book book) {
        if (hasText(book.getIsbn())) {
            String directUrl = DIRECT_COVER_URL.formatted(book.getIsbn().trim());

            Optional<CoverCandidate> direct = buildCandidate(
                    directUrl,
                    name(),
                    book.getTitle(),
                    "image/webp",
                    85
            );

            if (direct.isPresent()) {
                return direct;
            }
        }

        if (!hasText(book.getTitle())) {
            return Optional.empty();
        }

        String slug = slugify(book.getTitle());

        for (String category : CATEGORIES) {
            String productUrl = "https://fce.com.ar/tienda/" + category + "/" + slug + "/";

            Optional<String> imageUrl = extractOgImage(productUrl);

            if (imageUrl.isPresent()) {
                return Optional.of(new CoverCandidate(
                        imageUrl.get(),
                        book.getTitle(),
                        name(),
                        "image/webp",
                        80
                ));
            }
        }

        return Optional.empty();
    }

    @Override
    public String name() {
        return "FCE";
    }

    private Optional<String> extractOgImage(String productUrl) {
        try {
            Document document = Jsoup.connect(productUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            String imageUrl = document
                    .select("meta[property=og:image]")
                    .attr("content");

            if (!hasText(imageUrl)) {
                return Optional.empty();
            }

            return Optional.of(imageUrl.trim());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String slugify(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}