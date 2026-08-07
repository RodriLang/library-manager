package com.rodrilang.librarymanager.cover.job.repository;

import com.rodrilang.librarymanager.cover.job.entity.BookCoverJob;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookCoverJobClaimRepositoryImpl
        implements BookCoverJobClaimRepository {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public List<BookCoverJob> claimPendingJobs(
            int limit,
            LocalDateTime now
    ) {
        List<?> rawIds = entityManager
                .createNativeQuery("""
                        SELECT id
                        FROM book_cover_jobs
                        WHERE status = 'PENDING'
                          AND (
                              next_attempt_at IS NULL
                              OR next_attempt_at <= :now
                          )
                        ORDER BY created_at
                        FOR UPDATE SKIP LOCKED
                        LIMIT :limit
                        """)
                .setParameter("now", now)
                .setParameter("limit", limit)
                .getResultList();

        List<Long> jobIds = rawIds.stream()
                .map(value -> ((Number) value).longValue())
                .toList();

        if (jobIds.isEmpty()) {
            return List.of();
        }

        List<BookCoverJob> jobs = entityManager
                .createQuery("""
                        select job
                        from BookCoverJob job
                        where job.id in :jobIds
                        order by job.createdAt
                        """, BookCoverJob.class)
                .setParameter("jobIds", jobIds)
                .getResultList();

        jobs.forEach(BookCoverJob::markAsProcessing);

        entityManager.flush();

        return jobs;
    }
}