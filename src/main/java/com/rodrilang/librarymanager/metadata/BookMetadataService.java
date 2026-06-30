package com.rodrilang.librarymanager.metadata;

import java.util.Optional;

public interface BookMetadataService {

    Optional<BookMetadata> findByIsbn(String isbn);
}