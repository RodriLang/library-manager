package com.rodrilang.librarymanager.cover.mapper;

import com.rodrilang.librarymanager.cover.entity.BookCover;
import com.rodrilang.librarymanager.cover.dto.BookCoverResponse;
import org.springframework.stereotype.Component;

@Component
public class BookCoverMapper {

    public BookCoverResponse toResponse(
            BookCover cover,
            Long bookId
    ) {
        return new BookCoverResponse(
                cover.getId(),
                bookId,
                cover.getPublicId(),
                cover.getSecureUrl(),
                cover.getOriginalSourceUrl(),
                cover.getSource(),
                cover.getStatus(),
                cover.getFormat(),
                cover.getWidth(),
                cover.getHeight(),
                cover.getFileSize(),
                cover.isPrimaryCover(),
                cover.getCreatedAt(),
                cover.getUpdatedAt()
        );
    }
}