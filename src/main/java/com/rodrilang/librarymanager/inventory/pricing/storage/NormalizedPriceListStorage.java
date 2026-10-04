package com.rodrilang.librarymanager.inventory.pricing.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.media.configuration.CloudinaryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NormalizedPriceListStorage {

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;

    public StoredRawFile upload(Long bookstoreId, Path file) {
        try {
            String publicId = "price-list-" + UUID.randomUUID();
            String folder = properties.rootFolder() + "/bookstores/" + bookstoreId + "/price-lists";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.toFile(),
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "folder", folder,
                            "public_id", publicId,
                            "overwrite", false,
                            "use_filename", false,
                            "unique_filename", false
                    )
            );

            Object resultPublicId = result.get("public_id");
            Object secureUrl = result.get("secure_url");
            if (resultPublicId == null || secureUrl == null) {
                throw new BusinessException("Cloudinary no devolvió los datos del archivo normalizado.");
            }
            return new StoredRawFile(resultPublicId.toString(), secureUrl.toString());
        } catch (IOException exception) {
            throw new BusinessException("No se pudo guardar la copia normalizada de la lista de precios.");
        }
    }

    public record StoredRawFile(String publicId, String secureUrl) {
    }
}
