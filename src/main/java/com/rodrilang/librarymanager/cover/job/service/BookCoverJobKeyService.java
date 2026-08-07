package com.rodrilang.librarymanager.cover.job.service;

import com.rodrilang.librarymanager.media.hashing.ImageHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class BookCoverJobKeyService {

    private final ImageHashService imageHashService;

    public String generate(
            Long bookId,
            String normalizedSourceUrl
    ) {
        if (bookId == null) {
            throw new IllegalArgumentException(
                    "El id del libro es obligatorio"
            );
        }

        String source = bookId + "|" + normalizedSourceUrl;

        return imageHashService.sha256(
                source.getBytes(StandardCharsets.UTF_8)
        );
    }
}