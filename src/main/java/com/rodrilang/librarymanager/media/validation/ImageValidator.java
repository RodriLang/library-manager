package com.rodrilang.librarymanager.media.validation;

import com.rodrilang.librarymanager.media.configuration.ImageProcessingProperties;
import com.rodrilang.librarymanager.media.exception.InvalidImageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ImageValidator {

    private final ImageProcessingProperties properties;

    public ValidatedImage validate(MultipartFile file) {
        if (file == null) {
            throw new InvalidImageException(
                    "La imagen es obligatoria"
            );
        }

        if (file.isEmpty()) {
            throw new InvalidImageException(
                    "La imagen no puede estar vacía"
            );
        }

        validateFileSize(file.getSize());

        byte[] content = readContent(file);

        ImageContentType detectedType = detectContentType(content);

        validateDeclaredContentType(
                file.getContentType(),
                detectedType
        );

        String filename = normalizeFilename(
                file.getOriginalFilename(),
                detectedType
        );

        return new ValidatedImage(
                content,
                filename,
                detectedType
        );
    }

    public ValidatedImage validate(
            byte[] content,
            String originalFilename,
            String declaredContentType
    ) {
        if (content == null || content.length == 0) {
            throw new InvalidImageException(
                    "La imagen descargada está vacía"
            );
        }

        validateFileSize(content.length);

        ImageContentType detectedType =
                detectContentType(content);

        validateRemoteDeclaredContentType(
                declaredContentType,
                detectedType
        );

        return new ValidatedImage(
                content,
                normalizeFilename(
                        originalFilename,
                        detectedType
                ),
                detectedType
        );
    }

    public void validateStoredDimensions(
            Integer width,
            Integer height
    ) {
        if (width == null || height == null) {
            throw new InvalidImageException(
                    "No fue posible determinar las dimensiones de la imagen"
            );
        }

        if (width < properties.minimumWidth()) {
            throw new InvalidImageException(
                    "La imagen debe tener al menos %d píxeles de ancho"
                            .formatted(properties.minimumWidth())
            );
        }

        if (height < properties.minimumHeight()) {
            throw new InvalidImageException(
                    "La imagen debe tener al menos %d píxeles de alto"
                            .formatted(properties.minimumHeight())
            );
        }
    }

    private void validateFileSize(long fileSize) {
        long maximumBytes = properties.maxFileSize().toBytes();

        if (fileSize > maximumBytes) {
            throw new InvalidImageException(
                    "La imagen supera el tamaño máximo permitido de %s"
                            .formatted(properties.maxFileSize())
            );
        }
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new InvalidImageException(
                    "No se pudo leer la imagen recibida",
                    exception
            );
        }
    }

    private ImageContentType detectContentType(byte[] content) {
        if (isJpeg(content)) {
            return ImageContentType.JPEG;
        }

        if (isPng(content)) {
            return ImageContentType.PNG;
        }

        if (isWebp(content)) {
            return ImageContentType.WEBP;
        }

        throw new InvalidImageException(
                "El archivo recibido no es una imagen JPEG, PNG o WEBP válida"
        );
    }

    private void validateDeclaredContentType(
            String declaredContentType,
            ImageContentType detectedType
    ) {
        String detectedMimeType = detectedType.getMimeType();

        if (
                !properties.allowedContentTypes()
                        .contains(detectedMimeType)
        ) {
            throw new InvalidImageException(
                    "El formato %s no está permitido"
                            .formatted(detectedMimeType)
            );
        }

        if (
                declaredContentType == null
                        || declaredContentType.isBlank()
        ) {
            return;
        }

        String normalizedDeclaredType = declaredContentType
                .trim()
                .toLowerCase(Locale.ROOT);

        // Algunos navegadores envían image/jpg.
        if ("image/jpg".equals(normalizedDeclaredType)) {
            normalizedDeclaredType = "image/jpeg";
        }

        if (!normalizedDeclaredType.equals(detectedMimeType)) {
            throw new InvalidImageException(
                    "El tipo declarado del archivo no coincide con su contenido real"
            );
        }
    }

    private String normalizeFilename(
            String originalFilename,
            ImageContentType type
    ) {
        if (
                originalFilename == null
                        || originalFilename.isBlank()
        ) {
            return "cover." + type.getExtension();
        }

        String filename = originalFilename
                .replace("\\", "/");

        int separatorIndex = filename.lastIndexOf('/');

        if (separatorIndex >= 0) {
            filename = filename.substring(separatorIndex + 1);
        }

        filename = filename
                .replaceAll("[\\r\\n]", "")
                .trim();

        if (filename.isBlank()) {
            return "cover." + type.getExtension();
        }

        return filename;
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && unsigned(content[0]) == 0xFF
                && unsigned(content[1]) == 0xD8
                && unsigned(content[2]) == 0xFF;
    }

    private boolean isPng(byte[] content) {
        int[] signature = {
                0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
        };

        if (content.length < signature.length) {
            return false;
        }

        for (int index = 0; index < signature.length; index++) {
            if (unsigned(content[index]) != signature[index]) {
                return false;
            }
        }

        return true;
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P';
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private void validateRemoteDeclaredContentType(
            String declaredContentType,
            ImageContentType detectedType
    ) {
        String detectedMimeType =
                detectedType.getMimeType();

        if (
                !properties.allowedContentTypes()
                        .contains(detectedMimeType)
        ) {
            throw new InvalidImageException(
                    "El formato %s no está permitido"
                            .formatted(detectedMimeType)
            );
        }

        if (
                declaredContentType == null
                        || declaredContentType.isBlank()
        ) {
            return;
        }

        String normalizedDeclaredType =
                declaredContentType
                        .split(";", 2)[0]
                        .trim()
                        .toLowerCase();

        if (
                normalizedDeclaredType.equals(
                        "application/octet-stream"
                )
        ) {
            return;
        }

        if (
                normalizedDeclaredType.startsWith("image/")
                        && !normalizedDeclaredType.equals(
                        detectedMimeType
                )
        ) {
            throw new InvalidImageException(
                    "El tipo HTTP de la imagen no coincide con su contenido real"
            );
        }

        if (!normalizedDeclaredType.startsWith("image/")) {
            throw new InvalidImageException(
                    "El recurso remoto no es una imagen"
            );
        }
    }
}