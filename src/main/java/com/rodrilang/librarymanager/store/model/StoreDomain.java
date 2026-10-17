package com.rodrilang.librarymanager.store.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_domains")
public class StoreDomain extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", nullable = false) private BookstoreStore store;
    @Column(nullable = false, unique = true, length = 255) private String hostname;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StoreDomainType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) @Builder.Default private StoreDomainStatus status = StoreDomainStatus.PENDING;
    @Column(name = "verification_token", length = 120) private String verificationToken;
    @Column(name = "verified_at") private Instant verifiedAt;
}
