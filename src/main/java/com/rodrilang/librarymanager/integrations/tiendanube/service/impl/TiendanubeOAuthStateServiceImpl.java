package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeOAuthState;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeOAuthStateRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOAuthStateService;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.service.BookstoreService;
import com.rodrilang.librarymanager.util.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TiendanubeOAuthStateServiceImpl implements TiendanubeOAuthStateService {

    private static final Duration STATE_EXPIRATION = Duration.ofMinutes(10);

    private static final int STATE_RANDOM_BYTES = 32;

    private final TiendanubeOAuthStateRepository stateRepository;
    private final BookstoreService bookstoreService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String create(Long bookstoreId) {

        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

        String state = generateSecureState();
        String stateHash = HashUtils.sha256(state);

        Instant now = Instant.now();

        TiendanubeOAuthState oauthState = TiendanubeOAuthState.builder()
                .stateHash(stateHash)
                .bookstore(bookstore)
                .createdAt(now)
                .expiresAt(now.plus(STATE_EXPIRATION))
                .build();

        stateRepository.save(oauthState);

        return state;
    }

    @Override
    @Transactional
    public Long validateAndConsume(String state) {

        validateStateParameter(state);

        String stateHash = HashUtils.sha256(state);

        TiendanubeOAuthState oauthState = stateRepository.findByStateHashForUpdate(stateHash)
                .orElseThrow(() -> new BusinessException("El estado OAuth es inválido."));

        Instant now = Instant.now();

        if (oauthState.getUsedAt() != null) {
            throw new BusinessException("El estado OAuth ya fue utilizado.");
        }

        if (!oauthState.getExpiresAt().isAfter(now)) {
            throw new BusinessException("El estado OAuth ha vencido.");
        }

        oauthState.setUsedAt(now);

        stateRepository.save(oauthState);

        return oauthState.getBookstore().getId();
    }

    private void validateStateParameter(String state) {
        if (state == null || state.isBlank()) {
            throw new BusinessException("El parámetro state es obligatorio.");
        }
    }

    private String generateSecureState() {
        byte[] randomBytes = new byte[STATE_RANDOM_BYTES];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}