package com.rodrilang.librarymanager.store.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.admin.integration.mercadopago.service.MercadoPagoPlatformConfigService;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.store.payment.client.MercadoPagoAccountClient;
import com.rodrilang.librarymanager.store.payment.client.MercadoPagoClient;
import com.rodrilang.librarymanager.store.payment.crypto.StoreSecretCipher;
import com.rodrilang.librarymanager.store.payment.dto.MercadoPagoAuthorizationResponse;
import com.rodrilang.librarymanager.store.payment.model.StoreMercadoPagoConfig;
import com.rodrilang.librarymanager.store.payment.repository.StoreMercadoPagoConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StoreMercadoPagoOAuthService {
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    private final StoreMercadoPagoOAuthStateService stateService;
    private final StoreMercadoPagoConfigRepository configRepository;
    private final BookstoreRepository bookstoreRepository;
    private final BookstoreContext bookstoreContext;
    private final StoreSecretCipher cipher;
    private final MercadoPagoClient client;
    private final MercadoPagoAccountClient accountClient;
    private final MercadoPagoPlatformConfigService platformConfigService;

    public MercadoPagoAuthorizationResponse createAuthorizationUrl() {
        requireApplicationCredentials();
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        var authState = stateService.create(bookstoreId);
        String url = UriComponentsBuilder.fromUriString("https://auth.mercadopago.com/authorization")
                .queryParam("client_id", platformConfigService.requireClientId())
                .queryParam("response_type", "code")
                .queryParam("platform_id", "mp")
                .queryParam("redirect_uri", redirectUri())
                .queryParam("state", authState.state())
                .queryParam("code_challenge", authState.codeChallenge())
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
        return new MercadoPagoAuthorizationResponse(url);
    }

    @Transactional
    public void handleCallback(String code, String state) {
        requireApplicationCredentials();
        if (code == null || code.isBlank()) {
            throw new BusinessException("Mercado Pago no devolvió el código de autorización.");
        }

        var consumed = stateService.validateAndConsume(state);
        JsonNode token = client.exchangeAuthorizationCode(
                platformConfigService.requireClientId(),
                platformConfigService.requireClientSecret(),
                code,
                redirectUri(),
                consumed.codeVerifier()
        );

        String accessToken = text(token, "access_token");
        JsonNode account = null;
        if (hasText(accessToken)) {
            try {
                account = accountClient.getCurrentUser(accessToken);
            } catch (BusinessException ignored) {
                // La vinculación OAuth sigue siendo válida aunque Mercado Pago no permita
                // recuperar temporalmente el perfil. El librero puede sincronizarlo luego.
            }
        }
        if (account != null) validateAccountIdentity(token, account);

        saveConnection(consumed.bookstoreId(), token, account, true);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public String requireAccessToken(Long bookstoreId) {
        requireApplicationCredentials();
        StoreMercadoPagoConfig config = configRepository.findByBookstoreIdForUpdate(bookstoreId)
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta de Mercado Pago conectada."));
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException("Mercado Pago no está habilitado para esta librería.");
        }
        return validAccessToken(config);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void syncCurrentAccount() {
        requireApplicationCredentials();
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        StoreMercadoPagoConfig config = configRepository.findByBookstoreIdForUpdate(bookstoreId)
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta de Mercado Pago conectada."));

        String accessToken = validAccessToken(config);
        JsonNode account = accountClient.getCurrentUser(accessToken);
        validateAccountIdentity(config.getMercadoPagoUserId(), account);
        applyAccount(config, account);
        configRepository.save(config);
    }

    private String validAccessToken(StoreMercadoPagoConfig config) {
        if (!hasText(config.getAccessTokenEncrypted()) || !hasText(config.getRefreshTokenEncrypted())) {
            throw new BusinessException("La conexión con Mercado Pago necesita volver a autorizarse.");
        }

        Instant expiresAt = config.getTokenExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now().plus(REFRESH_MARGIN))) {
            try {
                JsonNode refreshed = client.refreshToken(
                        platformConfigService.requireClientId(),
                        platformConfigService.requireClientSecret(),
                        cipher.decrypt(config.getRefreshTokenEncrypted())
                );
                applyToken(config, refreshed, false);
                configRepository.save(config);
            } catch (BusinessException ex) {
                config.setEnabled(false);
                config.setConnectionError("La autorización de Mercado Pago necesita renovarse. Volvé a conectar la cuenta.");
                configRepository.save(config);
                throw ex;
            }
        }

        return cipher.decrypt(config.getAccessTokenEncrypted());
    }

    private void saveConnection(Long bookstoreId, JsonNode token, JsonNode account, boolean newConnection) {
        StoreMercadoPagoConfig config = configRepository.findByBookstoreIdForUpdate(bookstoreId).orElseGet(() -> {
            Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                    .orElseThrow(() -> new BusinessException("No se encontró la librería."));
            return StoreMercadoPagoConfig.builder().bookstore(bookstore).enabled(false).build();
        });

        applyToken(config, token, newConnection);
        if (newConnection) clearAccount(config);
        if (account != null) applyAccount(config, account);
        config.setEnabled(true);
        configRepository.save(config);
    }

    private void applyToken(StoreMercadoPagoConfig config, JsonNode token, boolean newConnection) {
        String accessToken = text(token, "access_token");
        String refreshToken = text(token, "refresh_token");
        if (!hasText(accessToken) || !hasText(refreshToken)) {
            throw new BusinessException("Mercado Pago no devolvió las credenciales OAuth esperadas.");
        }
        config.setAccessTokenEncrypted(cipher.encrypt(accessToken));
        config.setRefreshTokenEncrypted(cipher.encrypt(refreshToken));
        if (token.hasNonNull("user_id")) config.setMercadoPagoUserId(token.path("user_id").asLong());
        if (token.hasNonNull("public_key")) config.setPublicKey(token.path("public_key").asText());
        config.setTokenType(text(token, "token_type"));
        config.setScope(text(token, "scope"));
        long expiresIn = token.path("expires_in").asLong(0);
        config.setTokenExpiresAt(expiresIn > 0 ? Instant.now().plusSeconds(expiresIn) : null);
        if (newConnection || config.getConnectedAt() == null) config.setConnectedAt(Instant.now());
        config.setDisconnectedAt(null);
        config.setConnectionError(null);
    }

    private void applyAccount(StoreMercadoPagoConfig config, JsonNode account) {
        if (account == null || account.isNull()) return;
        if (account.hasNonNull("id")) config.setMercadoPagoUserId(account.path("id").asLong());
        config.setAccountNickname(text(account, "nickname"));
        config.setAccountEmail(text(account, "email"));
        config.setAccountFirstName(text(account, "first_name"));
        config.setAccountLastName(text(account, "last_name"));
        config.setAccountCountryId(text(account, "country_id"));
        config.setTestAccount(testAccount(account));
    }

    private void clearAccount(StoreMercadoPagoConfig config) {
        config.setAccountNickname(null);
        config.setAccountEmail(null);
        config.setAccountFirstName(null);
        config.setAccountLastName(null);
        config.setAccountCountryId(null);
        config.setTestAccount(null);
    }


    private Boolean testAccount(JsonNode account) {
        JsonNode tags = account == null ? null : account.get("tags");
        if (tags == null || !tags.isArray()) return null;
        for (JsonNode tag : tags) {
            if ("test_user".equalsIgnoreCase(tag.asText())) return true;
        }
        return false;
    }

    private void validateAccountIdentity(JsonNode token, JsonNode account) {
        Long tokenUserId = token != null && token.hasNonNull("user_id") ? token.path("user_id").asLong() : null;
        validateAccountIdentity(tokenUserId, account);
    }

    private void validateAccountIdentity(Long expectedUserId, JsonNode account) {
        if (expectedUserId == null || account == null || !account.hasNonNull("id")) return;
        long actualUserId = account.path("id").asLong();
        if (actualUserId != expectedUserId) {
            throw new BusinessException("La cuenta devuelta por Mercado Pago no coincide con la cuenta autorizada.");
        }
    }

    public String redirectUri() {
        return platformConfigService.redirectUri();
    }

    private void requireApplicationCredentials() {
        if (!platformConfigService.isConfiguredAndEnabled()) {
            throw new BusinessException("Mercado Pago OAuth no está habilitado o completamente configurado por el administrador de Anaquel.");
        }
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
