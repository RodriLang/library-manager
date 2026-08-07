package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.enums.BookCoverSource;

public interface RemoteBookCoverImportService {

    void importCover(
            Long bookId,
            String sourceUrl,
            BookCoverSource source
    );
}