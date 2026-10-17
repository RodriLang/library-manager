package com.rodrilang.librarymanager.store.payment.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.service.BookstoreService;
import com.rodrilang.librarymanager.store.payment.crypto.StoreSecretCipher;
import com.rodrilang.librarymanager.store.payment.model.StoreMercadoPagoOAuthState;
import com.rodrilang.librarymanager.store.payment.repository.StoreMercadoPagoOAuthStateRepository;
import com.rodrilang.librarymanager.util.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class StoreMercadoPagoOAuthStateService {
    private static final Duration EXPIRATION = Duration.ofMinutes(10);
    private static final int RANDOM_BYTES = 48;

    private final StoreMercadoPagoOAuthStateRepository repository;
    private final BookstoreService bookstoreService;
    private final StoreSecretCipher cipher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthorizationState create(Long bookstoreId) {
        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);
        String state = randomUrlToken(32);
        String verifier = randomUrlToken(RANDOM_BYTES);
        String challenge = sha256Base64Url(verifier);
        Instant now = Instant.now();

        repository.save(StoreMercadoPagoOAuthState.builder()
                .bookstore(bookstore)
                .stateHash(HashUtils.sha256(state))
                .codeVerifierEncrypted(cipher.encrypt(verifier))
                .createdAt(now)
                .expiresAt(now.plus(EXPIRATION))
                .build());

        return new AuthorizationState(state, challenge);
    }

    @Transactional
    public ConsumedState validateAndConsume(String state) {
        if (state == null || state.isBlank()) throw new BusinessException("El parámetro state es obligatorio.");
        StoreMercadoPagoOAuthState oauthState = repository.findByStateHashForUpdate(HashUtils.sha256(state))
                .orElseThrow(() -> new BusinessException("El estado OAuth de Mercado Pago es inválido."));
        Instant now = Instant.now();
        if (oauthState.getUsedAt() != null) throw new BusinessException("El estado OAuth de Mercado Pago ya fue utilizado.");
        if (!oauthState.getExpiresAt().isAfter(now)) throw new BusinessException("La autorización de Mercado Pago venció. Volvé a iniciar la conexión.");
        oauthState.setUsedAt(now);
        repository.save(oauthState);
        return new ConsumedState(oauthState.getBookstore().getId(), cipher.decrypt(oauthState.getCodeVerifierEncrypted()));
    }

    private String randomUrlToken(int bytes) {
        byte[] data = new byte[bytes];
        secureRandom.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private String sha256Base64Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar PKCE para Mercado Pago.", e);
        }
    }

    public record AuthorizationState(String state, String codeChallenge) {}
    public record ConsumedState(Long bookstoreId, String codeVerifier) {}
}
