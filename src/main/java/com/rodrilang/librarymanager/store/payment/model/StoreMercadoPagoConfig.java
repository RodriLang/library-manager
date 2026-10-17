package com.rodrilang.librarymanager.store.payment.model;

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
@Table(name = "store_mercado_pago_configs")
public class StoreMercadoPagoConfig extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false, unique = true)
    private Bookstore bookstore;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(name = "access_token_encrypted", columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @Column(name = "mercado_pago_user_id")
    private Long mercadoPagoUserId;

    @Column(name = "account_email")
    private String accountEmail;

    @Column(name = "account_nickname")
    private String accountNickname;

    @Column(name = "account_first_name")
    private String accountFirstName;

    @Column(name = "account_last_name")
    private String accountLastName;

    @Column(name = "account_country_id", length = 10)
    private String accountCountryId;

    @Column(name = "test_account")
    private Boolean testAccount;

    @Column(name = "public_key")
    private String publicKey;

    @Column(name = "token_type", length = 40)
    private String tokenType;

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "connection_error", columnDefinition = "TEXT")
    private String connectionError;
}
