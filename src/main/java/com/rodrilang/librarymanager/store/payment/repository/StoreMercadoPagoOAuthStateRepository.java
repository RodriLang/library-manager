package com.rodrilang.librarymanager.store.payment.repository;

import com.rodrilang.librarymanager.store.payment.model.StoreMercadoPagoOAuthState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface StoreMercadoPagoOAuthStateRepository extends JpaRepository<StoreMercadoPagoOAuthState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from StoreMercadoPagoOAuthState state join fetch state.bookstore where state.stateHash = :stateHash")
    Optional<StoreMercadoPagoOAuthState> findByStateHashForUpdate(@Param("stateHash") String stateHash);

    @Modifying
    @Query("delete from StoreMercadoPagoOAuthState state where state.expiresAt < :expiredBefore or (state.usedAt is not null and state.usedAt < :usedBefore)")
    int deleteOldStates(@Param("expiredBefore") Instant expiredBefore, @Param("usedBefore") Instant usedBefore);
}
