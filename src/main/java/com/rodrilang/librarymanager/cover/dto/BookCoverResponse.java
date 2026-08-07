package com.rodrilang.librarymanager.cover.dto;

import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.cover.enums.BookCoverStatus;

import java.time.LocalDateTime;

public record BookCoverResponse(
        Long id,
        Long bookId,
        String publicId,
        String url,
        String originalSourceUrl,
        BookCoverSource source,
        BookCoverStatus status,
        String format,
        Integer width,
        Integer height,
        Long fileSize,
        boolean primaryCover,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}