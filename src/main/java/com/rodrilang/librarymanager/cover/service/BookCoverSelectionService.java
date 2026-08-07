package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.entity.BookCover;
import com.rodrilang.librarymanager.cover.exception.BookCoverDoesNotBelongToBookException;
import com.rodrilang.librarymanager.cover.exception.BookCoverNotFoundException;
import com.rodrilang.librarymanager.cover.mapper.BookCoverMapper;
import com.rodrilang.librarymanager.cover.repository.BookCoverRepository;
import com.rodrilang.librarymanager.cover.dto.BookCoverResponse;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookCoverSelectionService {

    private final BookRepository bookRepository;
    private final BookCoverRepository bookCoverRepository;
    private final BookCoverMapper bookCoverMapper;

    @Transactional
    public BookCoverResponse selectPrimary(
            Long bookId,
            Long coverId
    ) {
        Book book = findBook(bookId);
        BookCover cover = findCover(coverId);

        validateBelongsToBook(cover, bookId);

        if (cover.isPrimaryCover() && cover.isAvailable()) {
            return bookCoverMapper.toResponse(cover, bookId);
        }

        bookCoverRepository.clearPrimaryCover(bookId);

        cover.markAsPrimary();

        book.updateCover(
                cover.getSecureUrl(),
                cover.getSource().name()
        );

        bookCoverRepository.saveAndFlush(cover);
        bookRepository.saveAndFlush(book);

        return bookCoverMapper.toResponse(cover, bookId);
    }

    @Transactional
    public void clearPrimary(Long bookId) {
        Book book = findBook(bookId);

        bookCoverRepository.clearPrimaryCover(bookId);

        book.clearCover();

        bookRepository.saveAndFlush(book);
    }

    private Book findBook(Long bookId) {
        return bookRepository
                .findById(bookId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "No se encontró el libro con id " + bookId
                        )
                );
    }

    private BookCover findCover(Long coverId) {
        return bookCoverRepository
                .findById(coverId)
                .orElseThrow(
                        () -> new BookCoverNotFoundException(coverId)
                );
    }

    private void validateBelongsToBook(
            BookCover cover,
            Long bookId
    ) {
        if (!cover.belongsToBook(bookId)) {
            throw new BookCoverDoesNotBelongToBookException(
                    cover.getId(),
                    bookId
            );
        }
    }
}