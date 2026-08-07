package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.enums.BookCoverStatus;
import com.rodrilang.librarymanager.cover.repository.BookCoverRepository;
import com.rodrilang.librarymanager.model.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookCoverCandidatePolicy {

    private final BookCoverRepository bookCoverRepository;

    public CoverCandidateDecision decide(Book book) {
        if (!book.hasCoverCandidate()) {
            return CoverCandidateDecision.SKIP_NO_CANDIDATE;
        }

        if (book.hasManualCover()) {
            return CoverCandidateDecision.SKIP_MANUAL_COVER;
        }

        boolean alreadyProcessed =
                bookCoverRepository
                        .existsByBookIdAndOriginalSourceUrlAndStatus(
                                book.getId(),
                                book.getCoverCandidateUrl(),
                                BookCoverStatus.AVAILABLE
                        );

        if (alreadyProcessed) {
            return CoverCandidateDecision.SKIP_ALREADY_PROCESSED;
        }

        return CoverCandidateDecision.PROCESS;
    }
}