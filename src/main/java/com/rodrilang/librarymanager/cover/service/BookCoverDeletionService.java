package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.entity.BookCover;
import com.rodrilang.librarymanager.cover.enums.BookCoverStatus;
import com.rodrilang.librarymanager.cover.exception.BookCoverDoesNotBelongToBookException;
import com.rodrilang.librarymanager.cover.exception.BookCoverNotFoundException;
import com.rodrilang.librarymanager.cover.repository.BookCoverRepository;
import com.rodrilang.librarymanager.media.storage.ImageStorageService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCoverDeletionService {

    private final BookRepository bookRepository;
    private final BookCoverRepository bookCoverRepository;
    private final ImageStorageService imageStorageService;
    private final TransactionTemplate transactionTemplate;

    public void delete(
            Long bookId,
            Long coverId
    ) {
        String publicId = transactionTemplate.execute(
                status -> deleteFromDatabase(bookId, coverId)
        );

        if (publicId != null) {
            deleteFromStorageQuietly(publicId);
        }
    }

    private String deleteFromDatabase(
            Long bookId,
            Long coverId
    ) {
        Book book = findBook(bookId);
        BookCover cover = findCover(coverId);

        validateBelongsToBook(cover, bookId);

        if (!cover.isAvailable()) {
            return null;
        }

        boolean wasPrimary = cover.isPrimaryCover();

        cover.markAsDeleted();
        bookCoverRepository.save(cover);

        if (wasPrimary) {
            selectReplacementOrClear(book, coverId);
        }

        bookRepository.save(book);
        bookCoverRepository.flush();

        return cover.getPublicId();
    }

    private void selectReplacementOrClear(
            Book book,
            Long deletedCoverId
    ) {
        Optional<BookCover> replacement =
                bookCoverRepository
                        .findFirstByBookIdAndStatusAndIdNotOrderByCreatedAtDesc(
                                book.getId(),
                                BookCoverStatus.AVAILABLE,
                                deletedCoverId
                        );

        if (replacement.isPresent()) {
            BookCover newPrimary = replacement.get();

            bookCoverRepository.clearPrimaryCover(book.getId());

            newPrimary.markAsPrimary();

            book.updateCover(
                    newPrimary.getSecureUrl(),
                    newPrimary.getSource().name()
            );

            bookCoverRepository.save(newPrimary);
            return;
        }

        bookCoverRepository.clearPrimaryCover(book.getId());
        book.clearCover();
    }

    private Book findBook(Long bookId) {
        return bookRepository
                .findById(bookId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "No se encontró el libro con id " + bookId
                        )
                );
    }

    private BookCover findCover(Long coverId) {
        return bookCoverRepository
                .findById(coverId)
                .orElseThrow(
                        () -> new BookCoverNotFoundException(coverId)
                );
    }

    private void validateBelongsToBook(
            BookCover cover,
            Long bookId
    ) {
        if (!cover.belongsToBook(bookId)) {
            throw new BookCoverDoesNotBelongToBookException(
                    cover.getId(),
                    bookId
            );
        }
    }

    private void deleteFromStorageQuietly(String publicId) {
        try {
            imageStorageService.delete(publicId);
        } catch (RuntimeException exception) {
            log.error(
                    "La portada {} fue eliminada de Anaquel, "
                            + "pero no pudo eliminarse del almacenamiento",
                    publicId,
                    exception
            );
        }
    }
}