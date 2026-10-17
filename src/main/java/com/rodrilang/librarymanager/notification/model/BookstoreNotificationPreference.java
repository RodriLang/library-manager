package com.rodrilang.librarymanager.notification.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "bookstore_notification_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uq_bookstore_notification_preference", columnNames = {"bookstore_id", "event_type"})
)
public class BookstoreNotificationPreference extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private NotificationType eventType;

    @Builder.Default
    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Builder.Default
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;
}
