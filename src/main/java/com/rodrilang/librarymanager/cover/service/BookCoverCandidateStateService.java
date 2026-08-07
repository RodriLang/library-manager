package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BookCoverCandidateStateService {

    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public BookCoverCandidateSnapshot getSnapshot(Long bookId) {
        Book book = findBook(bookId);

        return new BookCoverCandidateSnapshot(
                book.getId(),
                book.getCoverCandidateUrl(),
                book.getCoverCandidateAttempts(),
                book.getCoverUrl(),
                book.getCoverSource()
        );
    }

    @Transactional
    public void clear(Long bookId) {
        Book book = findBook(bookId);
        book.clearCoverCandidate();
    }

    @Transactional
    public void scheduleRetry(
            Long bookId,
            String error,
            Instant nextAttemptAt
    ) {
        Book book = findBook(bookId);
        book.scheduleCoverCandidateRetry(
                error,
                nextAttemptAt
        );
    }

    @Transactional
    public void fail(
            Long bookId,
            String error
    ) {
        Book book = findBook(bookId);
        book.failCoverCandidate(error);
    }

    private Book findBook(Long bookId) {
        return bookRepository
                .findById(bookId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No se encontró el libro " + bookId
                        )
                );
    }
}