package com.rodrilang.librarymanager.cover.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookCoverCandidateRepositoryImpl
        implements BookCoverCandidateRepository {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public List<Long> claimPendingCandidateIds(
            int limit,
            Instant now
    ) {
        List<?> rawIds = entityManager
                .createNativeQuery("""
                        SELECT id
                        FROM books
                        WHERE cover_candidate_url IS NOT NULL
                          AND cover_candidate_status = 'PENDING'
                          AND (
                              cover_candidate_next_attempt_at IS NULL
                              OR cover_candidate_next_attempt_at <= :now
                          )
                        ORDER BY id
                        FOR UPDATE SKIP LOCKED
                        LIMIT :limit
                        """)
                .setParameter("now", now)
                .setParameter("limit", limit)
                .getResultList();

        List<Long> ids = rawIds.stream()
                .map(value -> ((Number) value).longValue())
                .toList();

        if (ids.isEmpty()) {
            return List.of();
        }

        entityManager
                .createNativeQuery("""
                        UPDATE books
                        SET cover_candidate_status = 'PROCESSING',
                            cover_candidate_attempts =
                                cover_candidate_attempts + 1,
                            cover_candidate_next_attempt_at = NULL,
                            cover_candidate_error = NULL,
                            cover_candidate_started_at = :now
                        WHERE id IN (:ids)
                        """)
                .setParameter("now", now)
                .setParameter("ids", ids)
                .executeUpdate();

        return ids;
    }

    @Override
    @Transactional
    public int recoverTimedOutCandidates(
            Instant startedBefore
    ) {
        return entityManager
                .createNativeQuery("""
                        UPDATE books
                        SET cover_candidate_status = 'PENDING',
                            cover_candidate_next_attempt_at = CURRENT_TIMESTAMP,
                            cover_candidate_started_at = NULL,
                            cover_candidate_error =
                                'El procesamiento anterior excedió el tiempo máximo.'
                        WHERE cover_candidate_status = 'PROCESSING'
                          AND cover_candidate_started_at < :startedBefore
                        """)
                .setParameter("startedBefore", startedBefore)
                .executeUpdate();
    }
}