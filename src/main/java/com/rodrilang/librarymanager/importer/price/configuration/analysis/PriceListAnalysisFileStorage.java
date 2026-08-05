package com.rodrilang.librarymanager.importer.price.configuration.analysis;

import com.rodrilang.librarymanager.exception.BusinessException;
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
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class PriceListAnalysisFileStorage {

    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_PERMISSIONS =
            EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );

    private final Path rootDirectory;

    public PriceListAnalysisFileStorage() {
        this.rootDirectory = createPrivateRootDirectory();
    }

    public Path store(MultipartFile file) {
        Path analysisDirectory = null;

        try {
            analysisDirectory = Files.createDirectory(
                    rootDirectory.resolve(UUID.randomUUID().toString())
            );

            applyOwnerOnlyPermissions(analysisDirectory);

            Path target = analysisDirectory.resolve("source.xlsx");

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(
                        inputStream,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            applyOwnerOnlyFilePermissions(target);

            return target;

        } catch (IOException exception) {
            deleteQuietly(analysisDirectory);

            throw new BusinessException(
                    "No se pudo almacenar temporalmente el archivo de análisis."
            );
        }
    }

    public void deleteQuietly(Path filePath) {
        if (filePath == null) {
            return;
        }

        Path directory = filePath.getParent();

        try {
            Files.deleteIfExists(filePath);

            if (
                    directory != null
                            && directory.startsWith(rootDirectory)
            ) {
                Files.deleteIfExists(directory);
            }
        } catch (IOException exception) {
            log.warn(
                    "No se pudo eliminar el archivo temporal de análisis. file={} directory={}",
                    filePath,
                    directory,
                    exception
            );
        }
    }

    private Path createPrivateRootDirectory() {
        try {
            Path systemTempDirectory =
                    Path.of(System.getProperty("java.io.tmpdir"));

            Path root = systemTempDirectory.resolve(
                    "library-manager-price-analysis"
            );

            Files.createDirectories(root);
            applyOwnerOnlyPermissions(root);

            return root.toAbsolutePath().normalize();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo preparar el almacenamiento temporal de análisis.",
                    exception
            );
        }
    }

    private void applyOwnerOnlyPermissions(Path path) throws IOException {
        if (supportsPosix(path)) {
            Files.setPosixFilePermissions(
                    path,
                    OWNER_ONLY_DIRECTORY_PERMISSIONS
            );
        }
    }

    private void applyOwnerOnlyFilePermissions(Path path) throws IOException {
        if (supportsPosix(path)) {
            Files.setPosixFilePermissions(
                    path,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE
                    )
            );
        }
    }

    private boolean supportsPosix(Path path) {
        return path.getFileSystem()
                .supportedFileAttributeViews()
                .contains("posix");
    }
}