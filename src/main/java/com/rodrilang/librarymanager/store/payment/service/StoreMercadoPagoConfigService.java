package com.rodrilang.librarymanager.store.payment.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.store.payment.crypto.StoreSecretCipher;
import com.rodrilang.librarymanager.store.payment.dto.MercadoPagoConfigResponse;
import com.rodrilang.librarymanager.store.payment.dto.UpdateMercadoPagoConfigRequest;
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

    @Transactional(readOnly = true)
    public MercadoPagoConfigResponse current() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return toResponse(repository.findByBookstoreId(bookstoreId).orElse(null));
    }

    @Transactional
    public MercadoPagoConfigResponse update(UpdateMercadoPagoConfigRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        StoreMercadoPagoConfig config = repository.findByBookstoreId(bookstoreId).orElseGet(() -> {
            Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                    .orElseThrow(() -> new BusinessException("No se encontró la librería."));
            return StoreMercadoPagoConfig.builder().bookstore(bookstore).enabled(false).build();
        });

        if (hasText(request.accessToken())) config.setAccessTokenEncrypted(cipher.encrypt(request.accessToken().trim()));
        if (hasText(request.webhookSecret())) config.setWebhookSecretEncrypted(cipher.encrypt(request.webhookSecret().trim()));

        boolean configured = hasText(config.getAccessTokenEncrypted()) && hasText(config.getWebhookSecretEncrypted());
        if (Boolean.TRUE.equals(request.enabled()) && !configured) {
            throw new BusinessException("Para activar Mercado Pago cargá el Access Token y la clave secreta del webhook.");
        }
        config.setEnabled(request.enabled());
        return toResponse(repository.save(config));
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long bookstoreId) {
        return repository.findByBookstoreId(bookstoreId)
                .map(c -> Boolean.TRUE.equals(c.getEnabled()) && hasText(c.getAccessTokenEncrypted()) && hasText(c.getWebhookSecretEncrypted()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Credentials requireCredentials(Long bookstoreId) {
        StoreMercadoPagoConfig config = repository.findByBookstoreId(bookstoreId)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElseThrow(() -> new BusinessException("Mercado Pago no está habilitado para esta librería."));
        if (!hasText(config.getAccessTokenEncrypted()) || !hasText(config.getWebhookSecretEncrypted())) {
            throw new BusinessException("La configuración de Mercado Pago está incompleta.");
        }
        return new Credentials(cipher.decrypt(config.getAccessTokenEncrypted()), cipher.decrypt(config.getWebhookSecretEncrypted()));
    }

    private MercadoPagoConfigResponse toResponse(StoreMercadoPagoConfig config) {
        boolean configured = config != null && hasText(config.getAccessTokenEncrypted()) && hasText(config.getWebhookSecretEncrypted());
        return new MercadoPagoConfigResponse(
                config != null && Boolean.TRUE.equals(config.getEnabled()),
                configured,
                configured ? "••••••••••••" : null,
                config != null && hasText(config.getWebhookSecretEncrypted()),
                normalizeBase(publicApiBaseUrl) + "/api/storefront/payments/mercado-pago/webhook"
        );
    }

    private String normalizeBase(String value) {
        String v = value == null ? "https://api.anaquel.com.ar" : value.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    public record Credentials(String accessToken, String webhookSecret) {}
}
