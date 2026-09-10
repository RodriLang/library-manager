package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import com.rodrilang.librarymanager.integrations.tiendanube.config.TiendanubeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TiendanubeWebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final TiendanubeProperties properties;

    public boolean isValid(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            return false;
        }

        String normalizedSignature = signature.trim().toLowerCase();
        if (!normalizedSignature.matches("[0-9a-f]{64}")) {
            return false;
        }

        String expected = calculate(payload, properties.clientSecret());

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                normalizedSignature.getBytes(StandardCharsets.US_ASCII)
        );
    }

    String calculate(String payload, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("El client secret de Tiendanube no está configurado");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo validar la firma del webhook de Tiendanube", exception);
        }
    }
}
