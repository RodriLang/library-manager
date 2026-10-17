package com.rodrilang.librarymanager.shipping.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookstore_shipping_integrations", uniqueConstraints = @UniqueConstraint(name = "uq_bookstore_shipping_integration", columnNames = {"bookstore_id", "provider"}))
public class BookstoreShippingIntegration extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ShippingProviderType provider;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShippingIntegrationStatus status = ShippingIntegrationStatus.NOT_CONFIGURED;

    @Column(name = "credentials_encrypted", columnDefinition = "TEXT")
    private String credentialsEncrypted;

    @Column(name = "settings_json", columnDefinition = "TEXT")
    private String settingsJson;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "configured_at")
    private Instant configuredAt;
}
