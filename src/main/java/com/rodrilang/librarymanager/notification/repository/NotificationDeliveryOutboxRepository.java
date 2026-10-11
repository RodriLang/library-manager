package com.rodrilang.librarymanager.notification.repository;

import com.rodrilang.librarymanager.notification.model.NotificationDeliveryOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationDeliveryOutboxRepository extends JpaRepository<NotificationDeliveryOutbox, Long> {

    @Query(value = """
            SELECT *
              FROM notification_delivery_outbox
             WHERE (
                    status IN ('PENDING', 'FAILED')
                    AND next_attempt_at <= CURRENT_TIMESTAMP
                    AND attempts < :maxAttempts
                   )
                OR (
                    status = 'PROCESSING'
                    AND updated_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                    AND attempts < :maxAttempts
                   )
             ORDER BY created_at
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationDeliveryOutbox> lockDueBatch(
            @Param("batchSize") int batchSize,
            @Param("maxAttempts") int maxAttempts
    );
}
