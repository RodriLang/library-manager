package com.rodrilang.librarymanager.media.storage.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rodrilang.librarymanager.media.exception.ImageStorageException;
import com.rodrilang.librarymanager.media.storage.ImageStorageService;
import com.rodrilang.librarymanager.media.storage.ImageUploadRequest;
import com.rodrilang.librarymanager.media.storage.StoredImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryImageStorageService implements ImageStorageService {

    private final Cloudinary cloudinary;

    @Override
    public StoredImage upload(ImageUploadRequest request) {
        try {
            Map<String, Object> options = createUploadOptions(request);

            Map<?, ?> result = cloudinary
                    .uploader()
                    .upload(request.content(), options);

            return mapStoredImage(result);
        } catch (IOException exception) {
            throw new ImageStorageException(
                    "No se pudo subir la imagen a Cloudinary",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new ImageStorageException(
                    "Cloudinary rechazó la carga de la imagen",
                    exception
            );
        }
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException(
                    "El publicId de la imagen es obligatorio"
            );
        }

        try {
            Map<?, ?> result = cloudinary
                    .uploader()
                    .destroy(
                            publicId,
                            ObjectUtils.asMap(
                                    "resource_type", "image",
                                    "invalidate", true
                            )
                    );

            String status = asString(result.get("result"));

            if (!"ok".equalsIgnoreCase(status)
                    && !"not found".equalsIgnoreCase(status)) {
                throw new ImageStorageException(
                        "Cloudinary no pudo eliminar la imagen. Resultado: " + status
                );
            }
        } catch (IOException exception) {
            throw new ImageStorageException(
                    "No se pudo eliminar la imagen de Cloudinary",
                    exception
            );
        }
    }

    private Map<String, Object> createUploadOptions(
            ImageUploadRequest request
    ) {
        Map<String, Object> options = new HashMap<>();

        options.put("resource_type", "image");
        options.put("folder", request.folder());
        options.put("overwrite", request.overwrite());
        options.put("unique_filename", true);
        options.put("use_filename", false);

        if (request.publicId() != null && !request.publicId().isBlank()) {
            options.put("public_id", request.publicId());
            options.put("unique_filename", false);
        }

        if (
                request.originalFilename() != null
                        && !request.originalFilename().isBlank()
        ) {
            options.put(
                    "context",
                    "original_filename=" + sanitizeContextValue(
                            request.originalFilename()
                    )
            );
        }

        return options;
    }

    private StoredImage mapStoredImage(Map<?, ?> result) {
        String publicId = requiredString(result, "public_id");
        String secureUrl = requiredString(result, "secure_url");

        return new StoredImage(
                publicId,
                secureUrl,
                asString(result.get("format")),
                asInteger(result.get("width")),
                asInteger(result.get("height")),
                asLong(result.get("bytes")),
                asString(result.get("resource_type"))
        );
    }

    private String requiredString(Map<?, ?> result, String key) {
        String value = asString(result.get(key));

        if (value == null || value.isBlank()) {
            throw new ImageStorageException(
                    "Cloudinary no devolvió el campo obligatorio: " + key
            );
        }

        return value;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String sanitizeContextValue(String value) {
        return value
                .replace("|", "_")
                .replace("=", "_")
                .replace("\"", "_")
                .trim();
    }
}