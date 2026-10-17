package com.rodrilang.librarymanager.notification.repository;

import com.rodrilang.librarymanager.notification.model.BookstoreNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookstoreNotificationSettingsRepository extends JpaRepository<BookstoreNotificationSettings, Long> {
}
