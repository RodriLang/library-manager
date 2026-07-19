package com.rodrilang.librarymanager.integrations.tiendanube.repository;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeOAuthState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TiendanubeOAuthStateRepository
        extends JpaRepository<TiendanubeOAuthState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT state
            FROM TiendanubeOAuthState state
            JOIN FETCH state.bookstore
            WHERE state.stateHash = :stateHash
            """)
    Optional<TiendanubeOAuthState> findByStateHashForUpdate(
            @Param("stateHash") String stateHash
    );

    @Modifying
    @Query("""
            DELETE FROM TiendanubeOAuthState state
            WHERE state.expiresAt < :expirationLimit
               OR state.usedAt IS NOT NULL
            """)
    int deleteExpiredOrUsedBefore(
            @Param("expirationLimit") Instant expirationLimit
    );

    @Modifying
    @Query("""
            DELETE FROM TiendanubeOAuthState state
            WHERE state.expiresAt < :expiredBefore
               OR (
                    state.usedAt IS NOT NULL
                    AND state.usedAt < :usedBefore
               )
            """)
    int deleteOldStates(
            @Param("expiredBefore") Instant expiredBefore,
            @Param("usedBefore") Instant usedBefore
    );
}