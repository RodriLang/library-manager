package com.rodrilang.librarymanager.media.download;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class RemoteImageUrlNormalizer {

    public String normalize(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "La URL de la imagen es obligatoria"
            );
        }

        String trimmed = sourceUrl.trim();

        try {
            URI uri = new URI(trimmed);

            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException(
                        "La URL de la imagen no es válida"
                );
            }

            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);

            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException(
                        "Solo se admiten URLs HTTP o HTTPS"
                );
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);

            URI normalized = new URI(
                    scheme,
                    uri.getUserInfo(),
                    host,
                    uri.getPort(),
                    normalizePath(uri.getPath()),
                    uri.getQuery(),
                    null
            );

            return normalized.toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "La URL de la imagen no es válida",
                    exception
            );
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }

        return path.replaceAll("/{2,}", "/");
    }
}