package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.entity.BookCover;
import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.cover.exception.RetryableCoverProcessingException;
import com.rodrilang.librarymanager.cover.repository.BookCoverRepository;
import com.rodrilang.librarymanager.media.download.DownloadedImage;
import com.rodrilang.librarymanager.media.download.RemoteImageDownloader;
import com.rodrilang.librarymanager.media.download.RemoteImageDownloaderResolver;
import com.rodrilang.librarymanager.media.exception.ImageStorageException;
import com.rodrilang.librarymanager.media.exception.RemoteImageDownloadException;
import com.rodrilang.librarymanager.media.hashing.ImageHashService;
import com.rodrilang.librarymanager.media.image.ImageOptimizer;
import com.rodrilang.librarymanager.media.image.OptimizedImage;
import com.rodrilang.librarymanager.media.storage.ImageFolderResolver;
import com.rodrilang.librarymanager.media.storage.ImageStorageService;
import com.rodrilang.librarymanager.media.storage.ImageUploadRequest;
import com.rodrilang.librarymanager.media.storage.StoredImage;
import com.rodrilang.librarymanager.media.validation.ImageValidator;
import com.rodrilang.librarymanager.media.validation.ValidatedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteBookCoverImportServiceImpl implements RemoteBookCoverImportService {

    private final RemoteImageDownloaderResolver downloaderResolver;
    private final ImageOptimizer imageOptimizer;

    private final ImageValidator imageValidator;
    private final ImageHashService imageHashService;
    private final ImageFolderResolver imageFolderResolver;
    private final ImageStorageService imageStorageService;

    private final BookCoverRepository bookCoverRepository;
    private final BookCoverPersistenceService persistenceService;

    @Override
    public void importCover(
            Long bookId,
            String sourceUrl,
            BookCoverSource source
    ) {
        DownloadedImage downloaded =
                download(sourceUrl);

        OptimizedImage optimized =
                imageOptimizer.optimize(
                        downloaded.content(),
                        downloaded.filename(),
                        downloaded.declaredContentType()
                );

        ValidatedImage validated =
                imageValidator.validate(
                        optimized.content(),
                        optimized.filename(),
                        optimized.optimized()
                                ? "image/jpeg"
                                : downloaded.declaredContentType()
                );

        String contentHash =
                imageHashService.sha256(
                        validated.content()
                );

        Optional<BookCover> duplicate =
                bookCoverRepository
                        .findByBookIdAndContentHash(
                                bookId,
                                contentHash
                        );

        if (duplicate.isPresent()) {
            persistenceService.completeUsingExistingCover(
                    bookId,
                    duplicate.get().getId()
            );

            return;
        }

        StoredImage storedImage =
                upload(
                        bookId,
                        validated
                );

        try {
            imageValidator.validateStoredDimensions(
                    storedImage.width(),
                    storedImage.height()
            );

            persistenceService.persistImportedPrimaryCover(
                    bookId,
                    storedImage,
                    source,
                    sourceUrl,
                    contentHash
            );
        } catch (RuntimeException exception) {
            deleteQuietly(storedImage.publicId());
            throw exception;
        }
    }

    private DownloadedImage download(String sourceUrl) {
        try {
            RemoteImageDownloader downloader =
                    downloaderResolver.resolve(sourceUrl);

            return downloader.download(sourceUrl);
        } catch (RemoteImageDownloadException exception) {
            if (exception.isRetryable()) {
                throw new RetryableCoverProcessingException(
                        exception.getMessage(),
                        exception
                );
            }

            throw exception;
        }
    }

    private StoredImage upload(
            Long bookId,
            ValidatedImage validated
    ) {
        ImageUploadRequest request =
                ImageUploadRequest.create(
                        validated.content(),
                        validated.originalFilename(),
                        imageFolderResolver.bookCovers(bookId),
                        UUID.randomUUID().toString()
                );

        try {
            return imageStorageService.upload(request);
        } catch (ImageStorageException exception) {
            throw new RetryableCoverProcessingException(
                    "No se pudo subir la imagen a Cloudinary.",
                    exception
            );
        }
    }

    private void deleteQuietly(String publicId) {
        try {
            imageStorageService.delete(publicId);
        } catch (RuntimeException exception) {
            log.error(
                    "No se pudo eliminar de Cloudinary la imagen huérfana {}",
                    publicId,
                    exception
            );
        }
    }
}