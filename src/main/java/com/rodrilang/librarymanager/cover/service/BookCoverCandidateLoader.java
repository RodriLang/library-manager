package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BookCoverCandidateLoader {

    private final BookRepository bookRepository;
    private final BookCoverCandidatePolicy policy;

    @Transactional(readOnly = true)
    public CandidateContext load(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow();

        return new CandidateContext(
                book.getId(),
                book.getCoverCandidateUrl(),
                book.getCoverCandidateAttempts(),
                policy.decide(book)
        );
    }
}