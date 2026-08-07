package com.rodrilang.librarymanager.media.download.http;

import com.rodrilang.librarymanager.media.configuration.ImageProcessingProperties;
import com.rodrilang.librarymanager.media.download.DownloadedImage;
import com.rodrilang.librarymanager.media.exception.RemoteImageDownloadException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Component
@RequiredArgsConstructor
public class RemoteImageHttpClient {

    private final RestClient remoteImageRestClient;
    private final ImageProcessingProperties properties;

    public DownloadedImage download(
            String requestUrl,
            String fallbackFilename
    ) {
        try {
            return remoteImageRestClient
                    .get()
                    .uri(requestUrl)
                    .accept(
                            org.springframework.http.MediaType.IMAGE_JPEG,
                            org.springframework.http.MediaType.IMAGE_PNG,
                            org.springframework.http.MediaType.valueOf(
                                    "image/webp"
                            ),
                            org.springframework.http.MediaType.ALL
                    )
                    .exchange((request, response) -> {
                        validateStatus(
                                response.getStatusCode()
                        );

                        HttpHeaders headers =
                                response.getHeaders();

                        validateDeclaredLength(headers);

                        byte[] content = readLimited(
                                response.getBody()
                        );

                        String declaredContentType =
                                headers.getContentType() != null
                                        ? headers
                                        .getContentType()
                                        .toString()
                                        : null;

                        String filename = resolveFilename(
                                headers,
                                requestUrl,
                                fallbackFilename
                        );

                        return new DownloadedImage(
                                content,
                                filename,
                                declaredContentType,
                                requestUrl
                        );
                    });
        } catch (RemoteImageDownloadException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new RemoteImageDownloadException(
                    "No se pudo descargar la imagen remota.",
                    true,
                    exception
            );
        }
    }

    private void validateStatus(HttpStatusCode status) {
        int value = status.value();

        if (status.is2xxSuccessful()) {
            return;
        }

        if (value == 404 || value == 410) {
            throw new RemoteImageDownloadException(
                    "La imagen remota no existe.",
                    false
            );
        }

        if (value == 401 || value == 403) {
            throw new RemoteImageDownloadException(
                    "La imagen remota no es pública o requiere autorización.",
                    false
            );
        }

        if (value == 408 || value == 429 || status.is5xxServerError()) {
            throw new RemoteImageDownloadException(
                    "El servidor remoto no pudo entregar la imagen temporalmente.",
                    true
            );
        }

        throw new RemoteImageDownloadException(
                "La descarga remota respondió con estado HTTP " + value + ".",
                false
        );
    }

    private void validateDeclaredLength(HttpHeaders headers) {
        long contentLength = headers.getContentLength();

        if (
                contentLength > 0
                        && contentLength
                        > properties.maxDownloadSize().toBytes()
        ) {
            throw new RemoteImageDownloadException(
                    "El archivo remoto supera el tamaño máximo permitido.",
                    false
            );
        }
    }

    private byte[] readLimited(InputStream inputStream) {
        if (inputStream == null) {
            throw new RemoteImageDownloadException(
                    "El servidor remoto devolvió una respuesta vacía.",
                    false
            );
        }

        long maxBytes =
                properties.maxDownloadSize().toBytes();

        try (
                inputStream;
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {
            byte[] buffer = new byte[8192];
            long total = 0;

            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                total += read;

                if (total > maxBytes) {
                    throw new RemoteImageDownloadException(
                            "El archivo remoto supera el tamaño máximo permitido.",
                            false
                    );
                }

                output.write(buffer, 0, read);
            }

            byte[] content = output.toByteArray();

            if (content.length == 0) {
                throw new RemoteImageDownloadException(
                        "El servidor remoto devolvió un archivo vacío.",
                        false
                );
            }

            return content;
        } catch (RemoteImageDownloadException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new RemoteImageDownloadException(
                    "Se interrumpió la descarga de la imagen.",
                    true,
                    exception
            );
        }
    }

    private String resolveFilename(
            HttpHeaders headers,
            String requestUrl,
            String fallbackFilename
    ) {
        if (
                headers.getContentDisposition() != null
                        && headers
                        .getContentDisposition()
                        .getFilename() != null
        ) {
            return sanitizeFilename(
                    headers
                            .getContentDisposition()
                            .getFilename()
            );
        }

        try {
            String path = URI.create(requestUrl).getPath();

            if (path != null && !path.isBlank()) {
                String segment = path.substring(
                        path.lastIndexOf('/') + 1
                );

                if (
                        !segment.isBlank()
                                && segment.contains(".")
                ) {
                    return sanitizeFilename(segment);
                }
            }
        } catch (RuntimeException ignored) {
            // Se usa el fallback.
        }

        return sanitizeFilename(fallbackFilename);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "cover";
        }

        return filename
                .replace("\\", "/")
                .substring(
                        filename.replace("\\", "/")
                                .lastIndexOf('/') + 1
                )
                .replaceAll("[\\r\\n]", "")
                .trim();
    }
}