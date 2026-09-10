package com.rodrilang.librarymanager.integrations.tiendanube.job.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncAttempt;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TiendanubeSyncAttemptRepository extends JpaRepository<TiendanubeSyncAttempt, Long> {

    @Modifying
    @Query("""
            UPDATE TiendanubeSyncAttempt attempt
            SET attempt.status = :status,
                attempt.completedAt = :completedAt,
                attempt.errorType = :errorType,
                attempt.errorMessage = :errorMessage
            WHERE attempt.job.id = :jobId
              AND attempt.status = :currentStatus
            """)
    int markProcessingAttempts(
            @Param("jobId") Long jobId,
            @Param("status") TiendanubeJobAttemptStatus status,
            @Param("currentStatus") TiendanubeJobAttemptStatus currentStatus,
            @Param("completedAt") Instant completedAt,
            @Param("errorType") String errorType,
            @Param("errorMessage") String errorMessage
    );
}
