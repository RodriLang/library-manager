package com.rodrilang.librarymanager.admin.integration.mercadopago.service;

import com.rodrilang.librarymanager.admin.integration.mercadopago.dto.AdminMercadoPagoPlatformConfigResponse;
import com.rodrilang.librarymanager.admin.integration.mercadopago.dto.UpdateAdminMercadoPagoPlatformConfigRequest;
import com.rodrilang.librarymanager.admin.integration.mercadopago.model.MercadoPagoPlatformConfig;
import com.rodrilang.librarymanager.admin.integration.mercadopago.repository.MercadoPagoPlatformConfigRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.store.payment.crypto.StoreSecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class MercadoPagoPlatformConfigService {
    private static final String DEFAULT_API_BASE_URL = "https://api.anaquel.com.ar";
    private static final String DEFAULT_FRONTEND_URL = "https://anaquel.com.ar";
    private static final String OAUTH_CALLBACK_PATH = "/api/store/payments/mercado-pago/oauth/callback";
    private static final String WEBHOOK_PATH = "/api/storefront/payments/mercado-pago/webhook";

    private final MercadoPagoPlatformConfigRepository repository;
    private final StoreSecretCipher cipher;

    @Value("${app.store.payments.mercado-pago.client-id:}")
    private String envClientId;
    @Value("${app.store.payments.mercado-pago.client-secret:}")
    private String envClientSecret;
    @Value("${app.store.payments.mercado-pago.webhook-secret:}")
    private String envWebhookSecret;
    @Value("${app.store.payments.mercado-pago.oauth-redirect-uri:}")
    private String envOauthRedirectUri;
    @Value("${app.public-api-base-url:https://api.anaquel.com.ar}")
    private String envPublicApiBaseUrl;
    @Value("${app.frontend-url:https://anaquel.com.ar}")
    private String envFrontendUrl;

    /**
     * Vista tolerante para el panel admin. Una configuración parcial o inválida debe poder
     * consultarse para que el administrador pueda corregirla; no debe provocar un 500.
     */
    @Transactional(readOnly = true)
    public AdminMercadoPagoPlatformConfigResponse adminView() {
        MercadoPagoPlatformConfig stored = repository.findById(MercadoPagoPlatformConfig.SINGLETON_ID).orElse(null);
        EffectiveConfig effective = effectiveLenient(stored);
        return new AdminMercadoPagoPlatformConfigResponse(
                effective.enabled(),
                isOperationallyConfigured(effective),
                cipher.isConfigured(),
                stored == null ? "ENVIRONMENT" : "DATABASE",
                effective.clientId(),
                hasText(effective.clientSecret()),
                mask(effective.clientSecret()),
                hasText(effective.webhookSecret()),
                mask(effective.webhookSecret()),
                effective.oauthRedirectUri(),
                webhookUrlIfAvailable(effective.publicApiBaseUrl()),
                effective.publicApiBaseUrl(),
                effective.frontendUrl()
        );
    }

    @Transactional
    public AdminMercadoPagoPlatformConfigResponse update(UpdateAdminMercadoPagoPlatformConfigRequest request) {
        MercadoPagoPlatformConfig config = repository.findById(MercadoPagoPlatformConfig.SINGLETON_ID)
                .orElseGet(this::newFromEnvironment);

        if (request.enabled() != null) config.setEnabled(request.enabled());
        if (request.clientId() != null) config.setClientId(trimToNull(request.clientId()));
        if (hasText(request.clientSecret())) config.setClientSecretEncrypted(cipher.encrypt(request.clientSecret().trim()));
        if (hasText(request.webhookSecret())) config.setWebhookSecretEncrypted(cipher.encrypt(request.webhookSecret().trim()));
        if (request.oauthRedirectUri() != null) config.setOauthRedirectUri(normalizeOptionalAbsoluteUrl(request.oauthRedirectUri(), "Redirect URI"));
        if (request.publicApiBaseUrl() != null) config.setPublicApiBaseUrl(normalizeRequiredBaseUrl(request.publicApiBaseUrl(), "URL pública de API"));
        if (request.frontendUrl() != null) config.setFrontendUrl(normalizeRequiredBaseUrl(request.frontendUrl(), "URL de Anaquel UI"));

        EffectiveConfig candidate = effectiveStrict(config);
        if (Boolean.TRUE.equals(config.getEnabled()) && !isOperationallyConfigured(candidate)) {
            throw new BusinessException("Completá Client ID, Client Secret, Webhook Secret, URLs válidas y la clave maestra de cifrado antes de habilitar Mercado Pago.");
        }
        repository.save(config);
        return adminView();
    }

    /**
     * Configuración efectiva tolerante. Se usa sólo para consultar estado/UI.
     */
    @Transactional(readOnly = true)
    public EffectiveConfig current() {
        return effectiveLenient(repository.findById(MercadoPagoPlatformConfig.SINGLETON_ID).orElse(null));
    }

    /**
     * Configuración efectiva estricta para operaciones reales de Mercado Pago.
     */
    @Transactional(readOnly = true)
    public EffectiveConfig requireOperationalConfig() {
        EffectiveConfig cfg = effectiveStrict(repository.findById(MercadoPagoPlatformConfig.SINGLETON_ID).orElse(null));
        if (!cfg.enabled()) {
            throw new BusinessException("Mercado Pago está deshabilitado por el administrador de Anaquel.");
        }
        if (!isOperationallyConfigured(cfg)) {
            throw new BusinessException("Mercado Pago no está completamente configurado por el administrador de Anaquel.");
        }
        return cfg;
    }

    public String requireClientId() {
        return requireOperationalConfig().clientId();
    }

    public String requireClientSecret() {
        return requireOperationalConfig().clientSecret();
    }

    public String requireWebhookSecret() {
        return requireOperationalConfig().webhookSecret();
    }

    /**
     * Nunca lanza por una URL o secreto faltante/incorrecto. Es un chequeo de disponibilidad.
     */
    public boolean isConfiguredAndEnabled() {
        try {
            EffectiveConfig cfg = current();
            return cfg.enabled() && isOperationallyConfigured(cfg);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public String redirectUri() {
        return requireOperationalConfig().oauthRedirectUri();
    }

    public String publicApiBaseUrl() {
        return requireOperationalConfig().publicApiBaseUrl();
    }

    public String frontendUrl() {
        return requireOperationalConfig().frontendUrl();
    }

    /**
     * Útil para respuestas de estado. Devuelve null si todavía no hay una URL de API válida.
     */
    public String webhookUrl() {
        return webhookUrlIfAvailable(current().publicApiBaseUrl());
    }

    private MercadoPagoPlatformConfig newFromEnvironment() {
        return MercadoPagoPlatformConfig.builder()
                .id(MercadoPagoPlatformConfig.SINGLETON_ID)
                .enabled(hasText(envClientId) && hasText(envClientSecret) && hasText(envWebhookSecret))
                .clientId(trimToNull(envClientId))
                .clientSecretEncrypted(hasText(envClientSecret) ? cipher.encrypt(envClientSecret.trim()) : null)
                .webhookSecretEncrypted(hasText(envWebhookSecret) ? cipher.encrypt(envWebhookSecret.trim()) : null)
                .oauthRedirectUri(trimToNull(envOauthRedirectUri))
                .publicApiBaseUrl(validBaseOrRaw(envPublicApiBaseUrl, DEFAULT_API_BASE_URL))
                .frontendUrl(validBaseOrRaw(envFrontendUrl, DEFAULT_FRONTEND_URL))
                .build();
    }

    private EffectiveConfig effectiveLenient(MercadoPagoPlatformConfig stored) {
        if (stored == null) {
            String api = validBaseOrRaw(envPublicApiBaseUrl, DEFAULT_API_BASE_URL);
            String frontend = validBaseOrRaw(envFrontendUrl, DEFAULT_FRONTEND_URL);
            String redirect = hasText(envOauthRedirectUri)
                    ? validAbsoluteOrRaw(envOauthRedirectUri)
                    : deriveRedirectIfPossible(api);
            boolean enabled = hasText(envClientId) && hasText(envClientSecret) && hasText(envWebhookSecret);
            return new EffectiveConfig(
                    enabled,
                    trimToNull(envClientId),
                    trimToNull(envClientSecret),
                    trimToNull(envWebhookSecret),
                    redirect,
                    api,
                    frontend
            );
        }

        String apiFallback = validBaseOrRaw(envPublicApiBaseUrl, DEFAULT_API_BASE_URL);
        String frontendFallback = validBaseOrRaw(envFrontendUrl, DEFAULT_FRONTEND_URL);
        String api = hasText(stored.getPublicApiBaseUrl()) ? stored.getPublicApiBaseUrl().trim() : apiFallback;
        String frontend = hasText(stored.getFrontendUrl()) ? stored.getFrontendUrl().trim() : frontendFallback;
        String redirect = hasText(stored.getOauthRedirectUri())
                ? stored.getOauthRedirectUri().trim()
                : deriveRedirectIfPossible(api);

        return new EffectiveConfig(
                Boolean.TRUE.equals(stored.getEnabled()),
                trimToNull(stored.getClientId()),
                decryptLenient(stored.getClientSecretEncrypted()),
                decryptLenient(stored.getWebhookSecretEncrypted()),
                redirect,
                api,
                frontend
        );
    }

    private EffectiveConfig effectiveStrict(MercadoPagoPlatformConfig stored) {
        if (stored == null) {
            String api = normalizeBaseOrDefault(envPublicApiBaseUrl, DEFAULT_API_BASE_URL);
            String frontend = normalizeBaseOrDefault(envFrontendUrl, DEFAULT_FRONTEND_URL);
            String redirect = hasText(envOauthRedirectUri)
                    ? normalizeAbsoluteUrl(envOauthRedirectUri, "Redirect URI")
                    : api + OAUTH_CALLBACK_PATH;
            boolean enabled = hasText(envClientId) && hasText(envClientSecret) && hasText(envWebhookSecret);
            return new EffectiveConfig(
                    enabled,
                    trimToNull(envClientId),
                    trimToNull(envClientSecret),
                    trimToNull(envWebhookSecret),
                    redirect,
                    api,
                    frontend
            );
        }

        String api = normalizeBaseOrDefault(
                stored.getPublicApiBaseUrl(),
                normalizeBaseOrDefault(envPublicApiBaseUrl, DEFAULT_API_BASE_URL)
        );
        String frontend = normalizeBaseOrDefault(
                stored.getFrontendUrl(),
                normalizeBaseOrDefault(envFrontendUrl, DEFAULT_FRONTEND_URL)
        );
        String redirect = hasText(stored.getOauthRedirectUri())
                ? normalizeAbsoluteUrl(stored.getOauthRedirectUri(), "Redirect URI")
                : api + OAUTH_CALLBACK_PATH;

        return new EffectiveConfig(
                Boolean.TRUE.equals(stored.getEnabled()),
                trimToNull(stored.getClientId()),
                decryptStrict(stored.getClientSecretEncrypted()),
                decryptStrict(stored.getWebhookSecretEncrypted()),
                redirect,
                api,
                frontend
        );
    }

    private String decryptStrict(String value) {
        return hasText(value) ? cipher.decrypt(value) : null;
    }

    private String decryptLenient(String value) {
        if (!hasText(value) || !cipher.isConfigured()) return null;
        try {
            return cipher.decrypt(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isOperationallyConfigured(EffectiveConfig cfg) {
        return cfg != null
                && cipher.isConfigured()
                && hasText(cfg.clientId())
                && hasText(cfg.clientSecret())
                && hasText(cfg.webhookSecret())
                && isAbsoluteHttpUrl(cfg.publicApiBaseUrl())
                && isAbsoluteHttpUrl(cfg.frontendUrl())
                && isAbsoluteHttpUrl(cfg.oauthRedirectUri());
    }

    private String webhookUrlIfAvailable(String apiBase) {
        if (!isAbsoluteHttpUrl(apiBase)) return null;
        return stripTrailingSlash(apiBase.trim()) + WEBHOOK_PATH;
    }

    private String deriveRedirectIfPossible(String apiBase) {
        if (!isAbsoluteHttpUrl(apiBase)) return null;
        return stripTrailingSlash(apiBase.trim()) + OAUTH_CALLBACK_PATH;
    }

    private String validBaseOrRaw(String value, String fallback) {
        String candidate = hasText(value) ? value.trim() : fallback;
        if (isAbsoluteHttpUrl(candidate)) return stripTrailingSlash(candidate);
        return candidate;
    }

    private String validAbsoluteOrRaw(String value) {
        String candidate = trimToNull(value);
        if (candidate == null) return null;
        return isAbsoluteHttpUrl(candidate) ? candidate : candidate;
    }

    private String normalizeOptionalAbsoluteUrl(String value, String label) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : normalizeAbsoluteUrl(trimmed, label);
    }

    private String normalizeRequiredBaseUrl(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) throw new BusinessException(label + " no puede quedar vacía.");
        return stripTrailingSlash(normalizeAbsoluteUrl(trimmed, label));
    }

    private String normalizeBaseOrDefault(String value, String fallback) {
        String candidate = hasText(value) ? value.trim() : fallback;
        return stripTrailingSlash(normalizeAbsoluteUrl(candidate, "URL"));
    }

    private String normalizeAbsoluteUrl(String value, String label) {
        if (!isAbsoluteHttpUrl(value)) {
            throw new BusinessException(label + " debe ser una URL absoluta http/https válida.");
        }
        return URI.create(value.trim()).toString();
    }

    private boolean isAbsoluteHttpUrl(String value) {
        if (!hasText(value)) return false;
        try {
            URI uri = URI.create(value.trim());
            return uri.getScheme() != null
                    && uri.getHost() != null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String stripTrailingSlash(String value) {
        String out = value;
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private String mask(String value) {
        if (!hasText(value)) return null;
        String v = value.trim();
        int visible = Math.min(4, v.length());
        return "••••••••" + v.substring(v.length() - visible);
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record EffectiveConfig(
            boolean enabled,
            String clientId,
            String clientSecret,
            String webhookSecret,
            String oauthRedirectUri,
            String publicApiBaseUrl,
            String frontendUrl
    ) {
    }
}
