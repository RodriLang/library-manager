package com.rodrilang.librarymanager.metadata.google;

import com.rodrilang.librarymanager.metadata.BookMetadata;
import com.rodrilang.librarymanager.metadata.BookMetadataProvider;
import com.rodrilang.librarymanager.metadata.google.dto.GoogleBookItemDto;
import com.rodrilang.librarymanager.metadata.google.dto.GoogleBooksResponse;
import com.rodrilang.librarymanager.metadata.google.dto.GoogleVolumeInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GoogleBooksProvider implements BookMetadataProvider {

    private final RestClient googleBooksRestClient;

    @Override
    public Optional<BookMetadata> findByIsbn(String isbn) {
        try {
            GoogleBooksResponse response = googleBooksRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/books/v1/volumes")
                            .queryParam("q", "isbn:" + isbn)
                            .build())
                    .retrieve()
                    .body(GoogleBooksResponse.class);

            if (response == null || response.totalItems() == null || response.totalItems() == 0
                    || response.items() == null || response.items().isEmpty()) {
                return Optional.empty();
            }

            GoogleVolumeInfoDto volume = response.items().stream()
                    .map(GoogleBookItemDto::volumeInfo)
                    .filter(v -> v != null && v.title() != null && !v.title().isBlank())
                    .findFirst()
                    .orElse(null);

            if (volume == null) {
                return Optional.empty();
            }

            PublicationPeriod publicationPeriod =
                    parsePublishedDate(volume.publishedDate());

            return Optional.of(new BookMetadata(
                    isbn,
                    trimToNull(volume.title()),
                    trimToNull(volume.subtitle()),
                    trimToNull(volume.description()),
                    volume.pageCount(),
                    trimToNull(volume.language()),
                    trimToNull(volume.publisher()),
                    resolveAuthors(volume),
                    publicationPeriod.year(),
                    publicationPeriod.month(),
                    resolveCoverUrl(volume)
            ));

        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    @Override
    public int order() {
        return 2;
    }

    private Set<String> resolveAuthors(GoogleVolumeInfoDto volume) {
        if (volume.authors() == null || volume.authors().isEmpty()) {
            return new LinkedHashSet<>();
        }

        return volume.authors().stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveCoverUrl(GoogleVolumeInfoDto volume) {
        if (volume.imageLinks() == null) {
            return null;
        }

        if (volume.imageLinks().thumbnail() != null) {
            return buildHighResolutionImageUrl(volume.imageLinks().thumbnail());
        }

        return normalizeImageUrl(volume.imageLinks().smallThumbnail());
    }

    private String buildHighResolutionImageUrl(String url) {

        String normalizedUrl = normalizeImageUrl(url);

        if (normalizedUrl == null) {
            return null;
        }

        return normalizedUrl
                .replace("&zoom=1", "&zoom=0")
                .replace("&edge=curl", "");
    }

    private String normalizeImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        return url.trim().replace("http://", "https://");
    }

    private PublicationPeriod parsePublishedDate(String publishedDate) {
        if (publishedDate == null || publishedDate.isBlank()) {
            return PublicationPeriod.empty();
        }

        String normalized = publishedDate.trim();

        if (normalized.matches("\\d{4}")) {
            return new PublicationPeriod(
                    Integer.parseInt(normalized),
                    null
            );
        }

        if (normalized.matches("\\d{4}-\\d{2}(-\\d{2})?")) {
            return new PublicationPeriod(
                    Integer.parseInt(normalized.substring(0, 4)),
                    Integer.parseInt(normalized.substring(5, 7))
            );
        }

        return PublicationPeriod.empty();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private record PublicationPeriod(
            Integer year,
            Integer month
    ) {

        private static PublicationPeriod empty() {
            return new PublicationPeriod(null, null);
        }
    }
}