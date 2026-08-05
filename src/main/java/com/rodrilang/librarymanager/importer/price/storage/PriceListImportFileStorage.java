package com.rodrilang.librarymanager.importer.price.storage;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.config.PriceListImportProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PriceListImportFileStorage {

    private final PriceListImportProperties properties;

    public Path store(MultipartFile file) {
        validate(file);

        try {
            Path directory = Files.createTempDirectory("price-import-");
            Path target = directory.resolve("source.xlsx");

            try (InputStream input = file.getInputStream()) {
                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return target;

        } catch (IOException exception) {
            throw new BusinessException(
                    "No se pudo almacenar temporalmente el archivo."
            );
        }
    }

    public void deleteQuietly(Path filePath) {
        if (filePath == null) {
            return;
        }

        try {
            Files.deleteIfExists(filePath);

            Path parent = filePath.getParent();

            if (parent != null) {
                Files.deleteIfExists(parent);
            }
        } catch (IOException ignored) {
            // Se puede registrar como warning.
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    "Debe seleccionar un archivo Excel."
            );
        }

        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BusinessException(
                    "El archivo supera el máximo permitido de "
                            + properties.maxFileSize().toMegabytes()
                            + " MB."
            );
        }

        String filename = file.getOriginalFilename();

        if (
                filename == null
                        || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")
        ) {
            throw new BusinessException(
                    "La importación requiere un archivo .xlsx."
            );
        }
    }
}