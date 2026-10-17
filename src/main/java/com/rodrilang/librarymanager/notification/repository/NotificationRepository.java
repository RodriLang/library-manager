package com.rodrilang.librarymanager.notification.repository;

import com.rodrilang.librarymanager.notification.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByBookstoreIdAndInAppVisibleTrue(Long bookstoreId, Pageable pageable);

    long countByBookstoreIdAndInAppVisibleTrueAndReadAtIsNull(Long bookstoreId);

    Optional<Notification> findByIdAndBookstoreIdAndInAppVisibleTrue(Long id, Long bookstoreId);

    Optional<Notification> findByBookstoreIdAndTypeAndSourceAndEntityTypeAndEntityId(
            Long bookstoreId,
            NotificationType type,
            NotificationSource source,
            NotificationEntityType entityType,
            Long entityId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
               SET n.readAt = :readAt
             WHERE n.bookstore.id = :bookstoreId
               AND n.inAppVisible = true
               AND n.readAt IS NULL
            """)
    int markAllRead(@Param("bookstoreId") Long bookstoreId, @Param("readAt") Instant readAt);
}
