package com.rodrilang.librarymanager.notification.repository;

import com.rodrilang.librarymanager.notification.model.BookstoreNotificationPreference;
import com.rodrilang.librarymanager.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookstoreNotificationPreferenceRepository extends JpaRepository<BookstoreNotificationPreference, Long> {
    List<BookstoreNotificationPreference> findAllByBookstoreId(Long bookstoreId);
    Optional<BookstoreNotificationPreference> findByBookstoreIdAndEventType(Long bookstoreId, NotificationType eventType);
}
