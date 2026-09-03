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
            WHERE (
                    job.status IN ('PENDING', 'RETRY_WAIT')
                    AND job.next_attempt_at <= :now
                  )
               OR (
                    job.status = 'PROCESSING'
                    AND job.lease_until IS NOT NULL
                    AND job.lease_until <= :now
                  )
            ORDER BY job.next_attempt_at, job.id
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Long> findClaimableIds(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Query("SELECT job FROM TiendanubeSyncJob job WHERE job.id IN :ids")
    List<TiendanubeSyncJob> findAllByIds(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM TiendanubeSyncJob job WHERE job.id = :jobId")
    Optional<TiendanubeSyncJob> findByIdForUpdate(@Param("jobId") Long jobId);
}
