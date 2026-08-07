package com.rodrilang.librarymanager.media.hashing;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ImageHashService {

    public String sha256(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("El contenido es obligatorio para calcular el hash");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(content);

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }
}