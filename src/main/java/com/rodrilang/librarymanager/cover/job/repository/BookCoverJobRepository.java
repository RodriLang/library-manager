package com.rodrilang.librarymanager.cover.job.repository;

import com.rodrilang.librarymanager.cover.job.entity.BookCoverJob;
import com.rodrilang.librarymanager.cover.job.enums.BookCoverJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookCoverJobRepository
        extends JpaRepository<BookCoverJob, Long> {

    Optional<BookCoverJob> findByJobKey(String jobKey);

    boolean existsByJobKey(String jobKey);

    List<BookCoverJob> findAllByBookIdOrderByCreatedAtDesc(
            Long bookId
    );

    List<BookCoverJob>
    findAllByStatusOrderByCreatedAtAsc(
            BookCoverJobStatus status,
            Pageable pageable
    );

    long countByStatus(BookCoverJobStatus status);

    List<BookCoverJob>
    findAllByStatusAndStartedAtBefore(
            BookCoverJobStatus status,
            LocalDateTime startedBefore
    );
}