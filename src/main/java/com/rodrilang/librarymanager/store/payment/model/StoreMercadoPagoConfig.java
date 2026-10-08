package com.rodrilang.librarymanager.store.payment.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_mercado_pago_configs")
public class StoreMercadoPagoConfig extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bookstore_id", nullable = false, unique = true) private Bookstore bookstore;
    @Column(nullable = false) @Builder.Default private Boolean enabled = false;
    @Column(name = "access_token_encrypted", columnDefinition = "TEXT") private String accessTokenEncrypted;
    @Column(name = "webhook_secret_encrypted", columnDefinition = "TEXT") private String webhookSecretEncrypted;
}
