package com.rodrilang.librarymanager.metadata.openlibrary;

import com.rodrilang.librarymanager.metadata.openlibrary.dto.OpenLibraryAuthorDto;
import com.rodrilang.librarymanager.metadata.openlibrary.dto.OpenLibraryBookResponse;
import com.rodrilang.librarymanager.metadata.BookMetadata;
import com.rodrilang.librarymanager.metadata.BookMetadataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OpenLibraryProvider implements BookMetadataProvider {

    private final RestClient openLibraryRestClient;

    @Override
    public Optional<BookMetadata> findByIsbn(String isbn) {
        try {
            String key = "ISBN:" + isbn;

            Map<String, OpenLibraryBookResponse> response = openLibraryRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/books")
                            .queryParam("bibkeys", key)
                            .queryParam("format", "json")
                            .queryParam("jscmd", "data")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || !response.containsKey(key)) {
                return Optional.empty();
            }

            OpenLibraryBookResponse book = response.get(key);

            return Optional.of(new BookMetadata(
                    isbn,
                    trimToNull(book.title()),
                    trimToNull(book.subtitle()),
                    trimToNull(book.description()),
                    book.numberOfPages(),
                    null,
                    resolvePublisher(book),
                    resolveAuthors(book),
                    null,
                    resolveCoverUrl(book)
            ));
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    @Override
    public int order() {
        return 1;
    }

    private String resolvePublisher(OpenLibraryBookResponse book) {
        if (book.publishers() == null || book.publishers().isEmpty()) {
            return null;
        }

        return trimToNull(book.publishers().getFirst().name());
    }

    private Set<String> resolveAuthors(OpenLibraryBookResponse book) {
        if (book.authors() == null || book.authors().isEmpty()) {
            return new LinkedHashSet<>();
        }

        return book.authors().stream()
                .map(OpenLibraryAuthorDto::name)
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveCoverUrl(OpenLibraryBookResponse book) {
        if (book.cover() == null) {
            return null;
        }

        if (book.cover().large() != null) {
            return book.cover().large();
        }

        if (book.cover().medium() != null) {
            return book.cover().medium();
        }

        return book.cover().small();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}