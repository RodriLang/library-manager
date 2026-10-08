package com.rodrilang.librarymanager.store.payment.crypto;

import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class StoreSecretCipher {
    private final String masterKey;
    private final SecureRandom random = new SecureRandom();

    public StoreSecretCipher(@Value("${app.store.payments.encryption-key:}") String masterKey) {
        this.masterKey = masterKey;
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return null;
        requireKey();
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar la credencial de pago.", e);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return null;
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(value);
            byte[] iv = java.util.Arrays.copyOfRange(all, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(all, 12, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar la credencial de pago.", e);
        }
    }

    private SecretKeySpec key() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private void requireKey() {
        if (masterKey == null || masterKey.isBlank()) {
            throw new BusinessException("Configurá STORE_PAYMENT_ENCRYPTION_KEY antes de guardar credenciales de pago.");
        }
    }
}
