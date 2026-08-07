package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.entity.BookCover;
import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.cover.enums.BookCoverStatus;
import com.rodrilang.librarymanager.cover.exception.BookCoverNotFoundException;
import com.rodrilang.librarymanager.cover.exception.DuplicateBookCoverException;
import com.rodrilang.librarymanager.cover.mapper.BookCoverMapper;
import com.rodrilang.librarymanager.cover.repository.BookCoverRepository;
import com.rodrilang.librarymanager.cover.dto.BookCoverResponse;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.media.storage.StoredImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookCoverService {

    private final BookCoverRepository bookCoverRepository;
    private final BookCoverMapper bookCoverMapper;

    @Transactional(readOnly = true)
    public List<BookCoverResponse> findAllByBookId(Long bookId) {
        return bookCoverRepository
                .findAllByBookIdOrderByCreatedAtDesc(bookId)
                .stream()
                .map(cover -> bookCoverMapper.toResponse(cover, bookId))
                .toList();
    }

    @Transactional(readOnly = true)
    public BookCoverResponse getPrimaryByBookId(Long bookId) {
        return bookCoverRepository
                .findByBookIdAndPrimaryCoverTrue(bookId)
                .map(cover -> bookCoverMapper.toResponse(cover, bookId))
                .orElseThrow(
                        () -> BookCoverNotFoundException
                                .primaryForBook(bookId)
                );
    }

    @Transactional(readOnly = true)
    public BookCover getById(Long coverId) {
        return bookCoverRepository
                .findById(coverId)
                .orElseThrow(
                        () -> new BookCoverNotFoundException(coverId)
                );
    }

    @Transactional(readOnly = true)
    public Optional<BookCover> findDuplicate(
            Long bookId,
            String contentHash
    ) {
        return bookCoverRepository.findByBookIdAndContentHash(
                bookId,
                contentHash
        );
    }

    @Transactional
    public BookCover create(
            Book book,
            StoredImage storedImage,
            BookCoverSource source,
            String originalSourceUrl,
            String contentHash,
            boolean primaryCover
    ) {
        Long bookId = book.getId();

        if (
                contentHash != null
                        && bookCoverRepository.existsByBookIdAndContentHash(
                        bookId,
                        contentHash
                )
        ) {
            throw new DuplicateBookCoverException(
                    bookId,
                    contentHash
            );
        }

        BookCover cover = BookCover.create(
                book,
                storedImage,
                source,
                originalSourceUrl,
                contentHash,
                primaryCover
        );

        return bookCoverRepository.save(cover);
    }

    @Transactional
    public void markAsDeleted(BookCover cover) {
        cover.markAsDeleted();
        bookCoverRepository.save(cover);
    }

    @Transactional(readOnly = true)
    public List<BookCoverResponse> findAvailableByBookId(
            Long bookId
    ) {
        return bookCoverRepository
                .findAllByBookIdAndStatusOrderByCreatedAtDesc(
                        bookId,
                        BookCoverStatus.AVAILABLE
                )
                .stream()
                .map(cover -> bookCoverMapper.toResponse(cover, bookId))
                .toList();
    }
}