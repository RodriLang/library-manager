package com.rodrilang.librarymanager.metadata.cover;

import com.rodrilang.librarymanager.metadata.cover.provider.CoverProvider;
import com.rodrilang.librarymanager.model.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CoverSearchService {

    private final List<CoverProvider> providers;

    public Optional<CoverCandidate> findCover(Book book) {
        return providers.stream()
                .map(provider -> provider.findCover(book))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}