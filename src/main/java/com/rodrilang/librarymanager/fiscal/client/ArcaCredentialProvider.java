package com.rodrilang.librarymanager.fiscal.client;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.fiscal.config.ArcaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ArcaCredentialProvider {

    private final ArcaProperties properties;

    public X509Certificate certificate() {
        validateConfiguration();

        try {
            byte[] bytes = decodePemOrBase64(properties.certificateBase64(), "CERTIFICATE");
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes));
        } catch (Exception exception) {
            throw new BusinessException("No se pudo leer el certificado configurado para ARCA.");
        }
    }

    public PrivateKey privateKey() {
        validateConfiguration();

        try {
            byte[] bytes = decodePemOrBase64(properties.privateKeyBase64(), "PRIVATE KEY");
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception exception) {
            throw new BusinessException(
                    "No se pudo leer la clave privada ARCA. Debe estar en formato PKCS#8 sin contraseña."
            );
        }
    }

    private void validateConfiguration() {
        if (!properties.enabled()) {
            throw new BusinessException("La integración con ARCA no está habilitada en este ambiente.");
        }
        if (properties.certificateBase64() == null || properties.certificateBase64().isBlank()) {
            throw new BusinessException("Falta configurar ARCA_CERTIFICATE_BASE64.");
        }
        if (properties.privateKeyBase64() == null || properties.privateKeyBase64().isBlank()) {
            throw new BusinessException("Falta configurar ARCA_PRIVATE_KEY_BASE64.");
        }
    }

    private byte[] decodePemOrBase64(String value, String pemType) {
        String normalized = new String(
                Base64.getDecoder().decode(value.replaceAll("\\s", "")),
                StandardCharsets.UTF_8
        );

        if (normalized.contains("-----BEGIN")) {
            normalized = normalized
                    .replace("-----BEGIN " + pemType + "-----", "")
                    .replace("-----END " + pemType + "-----", "")
                    .replaceAll("\\s", "");

            return Base64.getDecoder().decode(normalized);
        }

        return Base64.getDecoder().decode(value.replaceAll("\\s", ""));
    }
}
