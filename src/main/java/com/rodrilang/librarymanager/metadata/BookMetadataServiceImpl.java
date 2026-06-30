package com.rodrilang.librarymanager.metadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookMetadataServiceImpl implements BookMetadataService {

    private final List<BookMetadataProvider> providers;

    @Override
    public Optional<BookMetadata> findByIsbn(String isbn) {
        return providers.stream()
                .sorted(Comparator.comparingInt(BookMetadataProvider::order))
                .map(provider -> {
                    log.info("Buscando ISBN {} en {}", isbn, provider.getClass().getSimpleName());
                    Optional<BookMetadata> result = provider.findByIsbn(isbn);
                    log.info("Resultado en {}: {}", provider.getClass().getSimpleName(), result.isPresent());
                    return result;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}