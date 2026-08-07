package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.entity.BookCover;
import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.cover.mapper.BookCoverMapper;
import com.rodrilang.librarymanager.cover.repository.BookCoverRepository;
import com.rodrilang.librarymanager.cover.dto.BookCoverResponse;
import com.rodrilang.librarymanager.media.hashing.ImageHashService;
import com.rodrilang.librarymanager.media.storage.ImageFolderResolver;
import com.rodrilang.librarymanager.media.storage.ImageStorageService;
import com.rodrilang.librarymanager.media.storage.ImageUploadRequest;
import com.rodrilang.librarymanager.media.storage.StoredImage;
import com.rodrilang.librarymanager.media.validation.ImageValidator;
import com.rodrilang.librarymanager.media.validation.ValidatedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCoverUploadService {

    private final BookCoverRepository bookCoverRepository;
    private final BookCoverMapper bookCoverMapper;
    private final BookCoverPersistenceService persistenceService;

    private final ImageValidator imageValidator;
    private final ImageHashService imageHashService;
    private final ImageFolderResolver imageFolderResolver;
    private final ImageStorageService imageStorageService;

    public BookCoverResponse uploadManualCover(
            Long bookId,
            MultipartFile file
    ) {
        ValidatedImage validatedImage =
                imageValidator.validate(file);

        String contentHash = imageHashService.sha256(
                validatedImage.content()
        );

        Optional<BookCover> duplicate =
                bookCoverRepository.findByBookIdAndContentHash(
                        bookId,
                        contentHash
                );

        if (duplicate.isPresent()) {
            return handleDuplicate(
                    bookId,
                    duplicate.get()
            );
        }

        StoredImage storedImage = uploadToStorage(
                bookId,
                validatedImage
        );

        try {
            imageValidator.validateStoredDimensions(
                    storedImage.width(),
                    storedImage.height()
            );

            BookCover savedCover =
                    persistenceService.persistNewPrimaryCover(
                            bookId,
                            storedImage,
                            BookCoverSource.MANUAL_UPLOAD,
                            null,
                            contentHash
                    );

            return bookCoverMapper.toResponse(savedCover, bookId);
        } catch (RuntimeException exception) {
            deleteQuietly(storedImage.publicId());
            throw exception;
        }
    }

    private BookCoverResponse handleDuplicate(
            Long bookId,
            BookCover existingCover
    ) {
        BookCover selectedCover = existingCover;

        if (
                existingCover.isAvailable()
                        && !existingCover.isPrimaryCover()
        ) {
            selectedCover =
                    persistenceService.selectExistingAsPrimary(
                            bookId,
                            existingCover.getId()
                    );
        }

        return bookCoverMapper.toResponse(selectedCover, bookId);
    }

    private StoredImage uploadToStorage(
            Long bookId,
            ValidatedImage validatedImage
    ) {
        String folder = imageFolderResolver.bookCovers(bookId);

        ImageUploadRequest request =
                ImageUploadRequest.create(
                        validatedImage.content(),
                        validatedImage.originalFilename(),
                        folder,
                        UUID.randomUUID().toString()
                );

        return imageStorageService.upload(request);
    }

    private void deleteQuietly(String publicId) {
        try {
            imageStorageService.delete(publicId);
        } catch (RuntimeException cleanupException) {
            log.error(
                    "No se pudo eliminar la imagen huérfana {}",
                    publicId,
                    cleanupException
            );
        }
    }
}