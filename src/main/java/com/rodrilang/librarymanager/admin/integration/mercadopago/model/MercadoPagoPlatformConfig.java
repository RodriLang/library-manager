package com.rodrilang.librarymanager.admin.integration.mercadopago.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mercado_pago_platform_config")
public class MercadoPagoPlatformConfig extends AuditableEntity {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret_encrypted", columnDefinition = "TEXT")
    private String clientSecretEncrypted;

    @Column(name = "webhook_secret_encrypted", columnDefinition = "TEXT")
    private String webhookSecretEncrypted;

    @Column(name = "oauth_redirect_uri", columnDefinition = "TEXT")
    private String oauthRedirectUri;

    @Column(name = "public_api_base_url", columnDefinition = "TEXT")
    private String publicApiBaseUrl;

    @Column(name = "frontend_url", columnDefinition = "TEXT")
    private String frontendUrl;
}
