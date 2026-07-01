package com.rodrilang.librarymanager.metadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookMetadataServiceImpl implements BookMetadataService {

    private final List<BookMetadataProvider> providers;

    @Override
    public Optional<BookMetadata> findByIsbn(String isbn) {
        List<BookMetadata> results = providers.stream()
                .sorted(Comparator.comparingInt(BookMetadataProvider::order))
                .map(provider -> {
                    log.info("Buscando ISBN {} en {}", isbn, provider.getClass().getSimpleName());
                    Optional<BookMetadata> result = provider.findByIsbn(isbn);
                    log.info("Resultado en {}: {}", provider.getClass().getSimpleName(), result.isPresent());
                    log.info("Metadata obtenida en {}: {}", provider.getClass().getSimpleName(), result.orElse(null));
                    return result;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        if (results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(merge(isbn, results));
    }

    private BookMetadata merge(String isbn, List<BookMetadata> results) {
        return new BookMetadata(
                isbn,
                firstNonBlank(results, BookMetadata::title),
                firstNonBlank(results, BookMetadata::subtitle),
                firstNonBlank(results, BookMetadata::description),
                firstNonNull(results, BookMetadata::pageCount),
                firstNonBlank(results, BookMetadata::language),
                firstNonBlank(results, BookMetadata::publisher),
                mergeAuthors(results),
                firstNonNull(results, BookMetadata::publicationDate),
                firstNonBlank(results, BookMetadata::coverUrl)
        );
    }

    private String firstNonBlank(
            List<BookMetadata> results,
            Function<BookMetadata, String> extractor
    ) {
        return results.stream()
                .map(extractor)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private <T> T firstNonNull(
            List<BookMetadata> results,
            Function<BookMetadata, T> extractor
    ) {
        return results.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Set<String> mergeAuthors(List<BookMetadata> results) {
        return results.stream()
                .flatMap(metadata -> metadata.authors() == null
                        ? Stream.empty()
                        : metadata.authors().stream())
                .filter(author -> author != null && !author.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}