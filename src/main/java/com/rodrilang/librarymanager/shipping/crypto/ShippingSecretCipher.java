package com.rodrilang.librarymanager.shipping.crypto;

import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class ShippingSecretCipher {
    private static final int IV_LENGTH = 12;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public ShippingSecretCipher(@Value("${app.store.shipping.encryption-key:${app.store.payments.encryption-key:}}") String masterKey) {
        this.key = masterKey == null || masterKey.isBlank() ? null : sha256(masterKey.trim());
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new BusinessException("No pudimos proteger las credenciales de envío.");
        }
    }

    public String decrypt(String value) {
        if (value == null) return null;
        requireConfigured();
        try {
            byte[] all = Base64.getDecoder().decode(value);
            byte[] iv = Arrays.copyOfRange(all, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(all, IV_LENGTH, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BusinessException("No pudimos leer las credenciales de envío configuradas.");
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) throw new BusinessException("Configurá app.store.shipping.encryption-key para guardar credenciales de envío.");
    }

    private byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
