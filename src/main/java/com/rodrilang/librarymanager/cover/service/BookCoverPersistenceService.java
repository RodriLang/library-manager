package com.rodrilang.librarymanager.cover.service;

import com.rodrilang.librarymanager.cover.entity.BookCover;
import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.cover.repository.BookCoverRepository;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.media.storage.StoredImage;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookCoverPersistenceService {

    private final BookRepository bookRepository;
    private final BookCoverRepository bookCoverRepository;

    @Transactional
    public BookCover persistNewPrimaryCover(
            Long bookId,
            StoredImage storedImage,
            BookCoverSource source,
            String originalSourceUrl,
            String contentHash
    ) {
        Book book = bookRepository
                .findById(bookId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "No se encontró el libro con id " + bookId
                        )
                );

        bookCoverRepository.clearPrimaryCover(bookId);

        BookCover cover = BookCover.create(
                book,
                storedImage,
                source,
                originalSourceUrl,
                contentHash,
                true
        );

        BookCover savedCover =
                bookCoverRepository.saveAndFlush(cover);

        book.updateCover(savedCover.getSecureUrl(), savedCover.getSource().name());
        bookRepository.saveAndFlush(book);

        return savedCover;
    }

    @Transactional
    public BookCover selectExistingAsPrimary(
            Long bookId,
            Long coverId
    ) {
        Book book = bookRepository
                .findById(bookId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "No se encontró el libro con id " + bookId
                        )
                );

        BookCover cover = bookCoverRepository
                .findById(coverId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "No se encontró la portada con id " + coverId
                        )
                );

        if (!cover.belongsToBook(bookId)) {
            throw new IllegalArgumentException(
                    "La portada no pertenece al libro indicado"
            );
        }

        bookCoverRepository.clearPrimaryCover(bookId);

        cover.markAsPrimary();
        book.updateCover(cover.getSecureUrl(), cover.getSource().name());

        bookCoverRepository.saveAndFlush(cover);
        bookRepository.saveAndFlush(book);

        return cover;
    }

    @Transactional
    public BookCover persistImportedPrimaryCover(
            Long bookId,
            StoredImage storedImage,
            BookCoverSource source,
            String originalSourceUrl,
            String contentHash
    ) {
        Book book = findBook(bookId);

        bookCoverRepository.clearPrimaryCover(bookId);

        BookCover cover = BookCover.create(
                book,
                storedImage,
                source,
                originalSourceUrl,
                contentHash,
                true
        );

        BookCover savedCover =
                bookCoverRepository.saveAndFlush(cover);

        book.completeCoverCandidate(
                savedCover.getSecureUrl(),
                source.name()
        );

        bookRepository.saveAndFlush(book);

        return savedCover;
    }

    @Transactional
    public BookCover completeUsingExistingCover(
            Long bookId,
            Long coverId
    ) {
        Book book = findBook(bookId);

        BookCover cover = bookCoverRepository
                .findById(coverId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No se encontró la portada " + coverId
                        )
                );

        if (!cover.belongsToBook(bookId)) {
            throw new IllegalArgumentException(
                    "La portada no pertenece al libro indicado"
            );
        }

        bookCoverRepository.clearPrimaryCover(bookId);

        cover.markAsPrimary();

        book.completeCoverCandidate(
                cover.getSecureUrl(),
                cover.getSource().name()
        );

        bookCoverRepository.saveAndFlush(cover);
        bookRepository.saveAndFlush(book);

        return cover;
    }

    private Book findBook(Long bookId) {
        return bookRepository
                .findById(bookId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No se encontró el libro " + bookId
                        )
                );
    }
}