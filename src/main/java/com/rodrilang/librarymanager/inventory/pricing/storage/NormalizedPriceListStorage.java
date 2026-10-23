package com.rodrilang.librarymanager.inventory.pricing.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.media.configuration.CloudinaryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class NormalizedPriceListStorage {

    // Current Cloudinary account rejects raw files larger than 10 MiB.
    // Revisit this limit if the account or storage provider changes.
    static final long MAX_RAW_UPLOAD_BYTES = 10L * 1024L * 1024L;

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;

    public StoredRawFile upload(Long bookstoreId, Path file) {
        Path compressed = null;
        try {
            long originalBytes = Files.size(file);
            compressed = gzip(file);
            long compressedBytes = Files.size(compressed);
            log.info("PRICE IMPORT NORMALIZED GZIP bookstoreId={} originalBytes={} compressedBytes={} reductionPercent={}",
                    bookstoreId, originalBytes, compressedBytes,
                    originalBytes == 0 ? 0 : Math.round(100d * (originalBytes - compressedBytes) / originalBytes));

            if (compressedBytes > MAX_RAW_UPLOAD_BYTES) {
                throw new BusinessException("La copia normalizada comprimida supera el límite de 10 MiB de Cloudinary ("
                        + compressedBytes + " bytes). El preview se conserva, pero no se guardó el respaldo.");
            }

            String publicId = "price-list-" + UUID.randomUUID() + ".csv.gz";
            String folder = properties.rootFolder() + "/bookstores/" + bookstoreId + "/price-lists";
            Map<?, ?> result = cloudinary.uploader().upload(
                    compressed.toFile(),
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
            log.info("PRICE IMPORT NORMALIZED STORED bookstoreId={} originalBytes={} compressedBytes={} format=csv.gz",
                    bookstoreId, originalBytes, compressedBytes);
            return new StoredRawFile(resultPublicId.toString(), secureUrl.toString());
        } catch (IOException exception) {
            throw new BusinessException("No se pudo comprimir o guardar la copia normalizada de la lista de precios.");
        } finally {
            if (compressed != null) {
                try {
                    Files.deleteIfExists(compressed);
                } catch (IOException exception) {
                    log.warn("No se pudo eliminar archivo temporal normalizado comprimido: {}", compressed, exception);
                }
            }
        }
    }

    static Path gzip(Path input) throws IOException {
        Path output = Files.createTempFile("anaquel-price-list-normalized-", ".csv.gz");
        boolean success = false;
        try (InputStream source = Files.newInputStream(input);
             OutputStream destination = new GZIPOutputStream(Files.newOutputStream(output))) {
            source.transferTo(destination);
            success = true;
            return output;
        } finally {
            if (!success) {
                Files.deleteIfExists(output);
            }
        }
    }

    public record StoredRawFile(String publicId, String secureUrl) {
    }
}
