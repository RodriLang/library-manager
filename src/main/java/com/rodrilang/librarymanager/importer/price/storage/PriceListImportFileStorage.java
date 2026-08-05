package com.rodrilang.librarymanager.importer.price.storage;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.config.PriceListImportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceListImportFileStorage {

    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS =
            EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );

    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
            EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );

    private final PriceListImportProperties properties;

    private final Path rootDirectory = createPrivateRootDirectory();

    public Path store(MultipartFile file) {
        validate(file);

        Path directory = null;

        try {
            directory = Files.createDirectory(
                    rootDirectory.resolve(UUID.randomUUID().toString())
            );

            applyPermissions(directory, DIRECTORY_PERMISSIONS);

            Path target = directory.resolve("source.xlsx");

            try (InputStream input = file.getInputStream()) {
                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            applyPermissions(target, FILE_PERMISSIONS);

            return target;

        } catch (IOException exception) {
            deleteDirectoryQuietly(directory);

            throw new BusinessException(
                    "No se pudo almacenar temporalmente el archivo."
            );
        }
    }

    public void deleteQuietly(Path filePath) {
        if (filePath == null) {
            return;
        }

        Path normalizedFile = filePath.toAbsolutePath().normalize();

        Path parent = normalizedFile.getParent();

        try {
            if (!normalizedFile.startsWith(rootDirectory)) {
                log.warn(
                        "Se intentó eliminar un archivo temporal fuera del directorio permitido. file={}",
                        normalizedFile
                );
                return;
            }

            Files.deleteIfExists(normalizedFile);

            if (
                    parent != null
                            && parent.startsWith(rootDirectory)
            ) {
                Files.deleteIfExists(parent);
            }

        } catch (IOException exception) {
            log.warn(
                    "No se pudo eliminar el archivo temporal de importación. file={} directory={}",
                    normalizedFile,
                    parent,
                    exception
            );
        }
    }

    private Path createPrivateRootDirectory() {
        try {
            Path systemTemp =
                    Path.of(System.getProperty("java.io.tmpdir"));

            Path root = systemTemp
                    .resolve("library-manager-price-import")
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(root);

            applyPermissions(
                    root,
                    DIRECTORY_PERMISSIONS
            );

            return root;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo preparar el directorio temporal de importaciones.",
                    exception
            );
        }
    }

    private void applyPermissions(
            Path path,
            Set<PosixFilePermission> permissions
    ) throws IOException {
        if (
                path.getFileSystem()
                        .supportedFileAttributeViews()
                        .contains("posix")
        ) {
            Files.setPosixFilePermissions(
                    path,
                    permissions
            );
        }
    }

    private void deleteDirectoryQuietly(
            Path directory
    ) {
        if (directory == null) {
            return;
        }

        try {
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            log.warn(
                    "No se pudo eliminar el directorio temporal incompleto. directory={}",
                    directory,
                    exception
            );
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