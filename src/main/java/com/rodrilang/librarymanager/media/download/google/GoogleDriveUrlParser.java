package com.rodrilang.librarymanager.media.download.google;

import com.rodrilang.librarymanager.media.exception.RemoteImageDownloadException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GoogleDriveUrlParser {

    private static final Pattern FILE_PATH_PATTERN =
            Pattern.compile("/file/d/([^/]+)");

    private static final Pattern DOCUMENT_PATH_PATTERN =
            Pattern.compile("/(?:document|spreadsheets|presentation)/d/([^/]+)");

    public boolean supports(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }

        try {
            String host = new URI(sourceUrl.trim()).getHost();

            if (host == null) {
                return false;
            }

            String normalizedHost =
                    host.toLowerCase(Locale.ROOT);

            return normalizedHost.equals("drive.google.com")
                    || normalizedHost.endsWith(".drive.google.com")
                    || normalizedHost.equals(
                    "drive.usercontent.google.com"
            );
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    public String extractFileId(String sourceUrl) {
        URI uri = parseUri(sourceUrl);

        Optional<String> pathId = extractFromPath(
                uri.getPath(),
                FILE_PATH_PATTERN
        );

        if (pathId.isPresent()) {
            return pathId.get();
        }

        Optional<String> documentId = extractFromPath(
                uri.getPath(),
                DOCUMENT_PATH_PATTERN
        );

        if (documentId.isPresent()) {
            return documentId.get();
        }

        String queryId = findQueryParameter(
                uri.getRawQuery(),
                "id"
        );

        if (queryId != null && !queryId.isBlank()) {
            return queryId;
        }

        throw new RemoteImageDownloadException(
                "No se pudo obtener el identificador del archivo de Google Drive.",
                false
        );
    }

    public String buildDownloadUrl(String sourceUrl) {
        String fileId = extractFileId(sourceUrl);

        return "https://drive.usercontent.google.com/download"
                + "?id=" + fileId
                + "&export=download"
                + "&confirm=t";
    }

    private URI parseUri(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new RemoteImageDownloadException(
                    "La URL de Google Drive está vacía.",
                    false
            );
        }

        try {
            URI uri = new URI(sourceUrl.trim());

            if (
                    uri.getScheme() == null
                            || uri.getHost() == null
            ) {
                throw new RemoteImageDownloadException(
                        "La URL de Google Drive no es válida.",
                        false
                );
            }

            return uri;
        } catch (URISyntaxException exception) {
            throw new RemoteImageDownloadException(
                    "La URL de Google Drive no es válida.",
                    false,
                    exception
            );
        }
    }

    private Optional<String> extractFromPath(
            String path,
            Pattern pattern
    ) {
        if (path == null) {
            return Optional.empty();
        }

        Matcher matcher = pattern.matcher(path);

        if (!matcher.find()) {
            return Optional.empty();
        }

        return Optional.ofNullable(matcher.group(1));
    }

    private String findQueryParameter(
            String rawQuery,
            String parameterName
    ) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        return Arrays.stream(rawQuery.split("&"))
                .map(value -> value.split("=", 2))
                .filter(parts ->
                        parts.length == 2
                                && parameterName.equals(parts[0])
                )
                .map(parts -> URLDecoder.decode(
                        parts[1],
                        StandardCharsets.UTF_8
                ))
                .findFirst()
                .orElse(null);
    }
}