package com.rodrilang.librarymanager.metadata;

import java.util.Optional;

public interface BookMetadataProvider {

    Optional<BookMetadata> findByIsbn(String isbn);

    int order();
}
