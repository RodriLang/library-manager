package com.rodrilang.librarymanager.auth.services.impl;

import com.rodrilang.librarymanager.auth.services.SecureTokenService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class SecureTokenServiceImpl implements SecureTokenService {

    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("La cantidad de bytes debe ser mayor que cero.");
        }

        byte[] tokenBytes = new byte[bytes];
        secureRandom.nextBytes(tokenBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
    }

    @Override
    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("El token es obligatorio.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo generar el hash del token.", exception);
        }
    }
}