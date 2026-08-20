package com.rodrilang.librarymanager.auth.repositories;

import com.rodrilang.librarymanager.auth.models.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true,
                rt.revokedAt = :revokedAt
            WHERE rt.user.id = :userId
              AND rt.revoked = false
            """)
    int revokeAllByUserId(
            @Param("userId") Long userId,
            @Param("revokedAt") Instant revokedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rt
            FROM RefreshToken rt
            JOIN FETCH rt.user
            WHERE rt.tokenHash = :tokenHash
              AND rt.revoked = false
            """)
    Optional<RefreshToken> findActiveByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    int deleteByExpiresAtBefore(Instant now);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true,
                rt.revokedAt = :revokedAt
            WHERE rt.user.bookstore.id = :bookstoreId
              AND rt.revoked = false
            """)
    int revokeAllByBookstoreId(
            @Param("bookstoreId") Long bookstoreId,
            @Param("revokedAt") Instant revokedAt
    );
}