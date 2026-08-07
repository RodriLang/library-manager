package com.rodrilang.librarymanager.cover.job.repository;

import com.rodrilang.librarymanager.cover.job.entity.BookCoverJob;

import java.time.LocalDateTime;
import java.util.List;

public interface BookCoverJobClaimRepository {

    List<BookCoverJob> claimPendingJobs(
            int limit,
            LocalDateTime now
    );
}