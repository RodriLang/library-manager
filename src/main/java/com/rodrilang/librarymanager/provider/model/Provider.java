package com.rodrilang.librarymanager.provider.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "providers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_providers_code",
                columnNames = "code"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProviderType type = ProviderType.COMMERCIAL;

    @Column(name = "tax_id", length = 30)
    private String taxId;

    @Column(length = 160)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    @Builder.Default
    private ProviderVerificationStatus verificationStatus = ProviderVerificationStatus.VERIFIED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProviderSource source = ProviderSource.LEGACY;

    @Column(name = "created_by_bookstore_id")
    private Long createdByBookstoreId;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isPurchasable() {
        return active
                && type == ProviderType.COMMERCIAL
                && (verificationStatus == ProviderVerificationStatus.VERIFIED
                    || verificationStatus == ProviderVerificationStatus.PENDING_REVIEW);
    }
}
