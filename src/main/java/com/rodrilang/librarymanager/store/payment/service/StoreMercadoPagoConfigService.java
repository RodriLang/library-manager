package com.rodrilang.librarymanager.store.payment.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.store.payment.crypto.StoreSecretCipher;
import com.rodrilang.librarymanager.store.payment.dto.MercadoPagoConfigResponse;
import com.rodrilang.librarymanager.store.payment.model.StoreMercadoPagoConfig;
import com.rodrilang.librarymanager.store.payment.repository.StoreMercadoPagoConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreMercadoPagoConfigService {
    private final StoreMercadoPagoConfigRepository repository;
    private final BookstoreRepository bookstoreRepository;
    private final BookstoreContext bookstoreContext;
    private final StoreSecretCipher cipher;

    @Value("${app.public-api-base-url:https://api.anaquel.com.ar}")
    private String publicApiBaseUrl;

    @Value("${app.store.payments.mercado-pago.client-id:}")
    private String clientId;

    @Value("${app.store.payments.mercado-pago.client-secret:}")
    private String clientSecret;

    @Value("${app.store.payments.mercado-pago.webhook-secret:}")
    private String webhookSecret;

    @Transactional(readOnly = true)
    public MercadoPagoConfigResponse current() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return toResponse(repository.findByBookstoreId(bookstoreId).orElse(null));
    }

    @Transactional
    public MercadoPagoConfigResponse setEnabled(boolean enabled) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        StoreMercadoPagoConfig config = repository.findByBookstoreId(bookstoreId).orElseGet(() -> newConfig(bookstoreId));
        if (enabled && !applicationConfigured()) {
            throw new BusinessException("Mercado Pago no está completamente configurado en Anaquel API.");
        }
        if (enabled && !isConnected(config)) {
            throw new BusinessException("Conectá una cuenta de Mercado Pago antes de habilitar pagos online.");
        }
        config.setEnabled(enabled);
        return toResponse(repository.save(config));
    }

    @Transactional
    public MercadoPagoConfigResponse disconnect() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        StoreMercadoPagoConfig config = repository.findByBookstoreId(bookstoreId).orElseGet(() -> newConfig(bookstoreId));
        config.setEnabled(false);
        config.setAccessTokenEncrypted(null);
        config.setRefreshTokenEncrypted(null);
        config.setMercadoPagoUserId(null);
        config.setPublicKey(null);
        config.setTokenType(null);
        config.setScope(null);
        config.setTokenExpiresAt(null);
        config.setConnectedAt(null);
        config.setDisconnectedAt(java.time.Instant.now());
        config.setConnectionError(null);
        return toResponse(repository.save(config));
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long bookstoreId) {
        return repository.findByBookstoreId(bookstoreId)
                .map(c -> applicationConfigured() && Boolean.TRUE.equals(c.getEnabled()) && isConnected(c) && !hasText(c.getConnectionError()))
                .orElse(false);
    }

    public boolean applicationConfigured() {
        return hasText(clientId) && hasText(clientSecret) && hasText(webhookSecret) && cipher.isConfigured();
    }

    public String webhookSecret() {
        if (!hasText(webhookSecret)) {
            throw new BusinessException("Falta configurar MERCADO_PAGO_WEBHOOK_SECRET en Anaquel API.");
        }
        return webhookSecret;
    }

    public String webhookUrl() {
        return normalizeBase(publicApiBaseUrl) + "/api/storefront/payments/mercado-pago/webhook";
    }

    private StoreMercadoPagoConfig newConfig(Long bookstoreId) {
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new BusinessException("No se encontró la librería."));
        return StoreMercadoPagoConfig.builder().bookstore(bookstore).enabled(false).build();
    }

    private MercadoPagoConfigResponse toResponse(StoreMercadoPagoConfig config) {
        boolean connected = config != null && isConnected(config);
        return new MercadoPagoConfigResponse(
                applicationConfigured(),
                connected,
                connected && applicationConfigured() && Boolean.TRUE.equals(config.getEnabled()) && !hasText(config.getConnectionError()),
                config != null && hasText(config.getConnectionError()),
                connected ? config.getMercadoPagoUserId() : null,
                connected ? config.getConnectedAt() : null,
                connected ? config.getTokenExpiresAt() : null,
                config == null ? null : config.getConnectionError(),
                webhookUrl()
        );
    }

    private boolean isConnected(StoreMercadoPagoConfig config) {
        return hasText(config.getAccessTokenEncrypted())
                && hasText(config.getRefreshTokenEncrypted())
                && config.getMercadoPagoUserId() != null;
    }

    private String normalizeBase(String value) {
        String v = value == null ? "https://api.anaquel.com.ar" : value.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
