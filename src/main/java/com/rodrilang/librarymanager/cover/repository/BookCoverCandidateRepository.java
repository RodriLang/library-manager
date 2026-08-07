package com.rodrilang.librarymanager.cover.repository;

import java.time.Instant;
import java.util.List;

public interface BookCoverCandidateRepository {

    List<Long> claimPendingCandidateIds(int limit, Instant now);

    int recoverTimedOutCandidates(Instant startedBefore);
}