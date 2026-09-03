package com.rodrilang.librarymanager.integrations.tiendanube.job.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TiendanubeSyncJobRepository extends JpaRepository<TiendanubeSyncJob, Long> {

    @Query(value = """
            SELECT job.id
            FROM tiendanube_sync_jobs job
            JOIN (
                SELECT DISTINCT ON (candidate.tiendanube_store_id) candidate.id
                FROM tiendanube_sync_jobs candidate
                JOIN tiendanube_stores store
                  ON store.id = candidate.tiendanube_store_id
                 AND store.active = TRUE
                 AND store.token_valid = TRUE
                LEFT JOIN tiendanube_api_rate_limits rate_limit
                       ON rate_limit.tiendanube_store_id = candidate.tiendanube_store_id
                      AND rate_limit.remote_store_id = candidate.store_id
                WHERE (
                        (
                            candidate.status IN ('PENDING', 'RETRY_WAIT')
                            AND candidate.next_attempt_at <= :now
                        )
                        OR (
                            candidate.status = 'PROCESSING'
                            AND candidate.lease_until IS NOT NULL
                            AND candidate.lease_until <= :now
                        )
                      )
                  AND (rate_limit.blocked_until IS NULL OR rate_limit.blocked_until <= :now)
                ORDER BY candidate.tiendanube_store_id, candidate.next_attempt_at, candidate.id
            ) selected ON selected.id = job.id
            ORDER BY job.next_attempt_at, job.id
            FOR UPDATE OF job SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Long> findClaimableIds(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Query("SELECT job FROM TiendanubeSyncJob job WHERE job.id IN :ids")
    List<TiendanubeSyncJob> findAllByIds(@Param("ids") List<Long> ids);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM tiendanube_sync_jobs job
                WHERE job.tiendanube_store_id = :tiendanubeStoreId
                  AND job.id <> :excludedJobId
                  AND job.status = 'PROCESSING'
                  AND job.lease_until IS NOT NULL
                  AND job.lease_until > :now
            )
            """, nativeQuery = true)
    boolean existsActiveProcessingForStore(
            @Param("tiendanubeStoreId") Long tiendanubeStoreId,
            @Param("excludedJobId") Long excludedJobId,
            @Param("now") Instant now
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM tiendanube_sync_jobs job
                WHERE job.inventory_id = :inventoryId
                  AND job.type = :type
                  AND job.id <> :excludedJobId
                  AND job.status IN ('PENDING', 'RETRY_WAIT')
            )
            """, nativeQuery = true)
    boolean existsPendingSuccessor(
            @Param("inventoryId") Long inventoryId,
            @Param("type") String type,
            @Param("excludedJobId") Long excludedJobId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM TiendanubeSyncJob job WHERE job.id = :jobId")
    Optional<TiendanubeSyncJob> findByIdForUpdate(@Param("jobId") Long jobId);
}
