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
@Table(name = "bookstore_notification_settings")
public class BookstoreNotificationSettings extends AuditableEntity {

    @Id
    @Column(name = "bookstore_id")
    private Long bookstoreId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "bookstore_id")
    private Bookstore bookstore;

    @Builder.Default
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;

    @Column(name = "email_address", length = 320)
    private String emailAddress;
}
