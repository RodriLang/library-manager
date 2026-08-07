package com.rodrilang.librarymanager.cover.repository;

import com.rodrilang.librarymanager.cover.entity.BookCover;
import com.rodrilang.librarymanager.cover.enums.BookCoverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookCoverRepository
        extends JpaRepository<BookCover, Long> {

    List<BookCover> findAllByBookIdOrderByCreatedAtDesc(Long bookId);

    List<BookCover> findAllByBookIdAndStatusOrderByCreatedAtDesc(
            Long bookId,
            BookCoverStatus status
    );

    Optional<BookCover> findByBookIdAndPrimaryCoverTrue(Long bookId);

    Optional<BookCover> findByPublicId(String publicId);

    Optional<BookCover> findByBookIdAndContentHash(
            Long bookId,
            String contentHash
    );

    Optional<BookCover> findFirstByBookIdAndStatusAndIdNotOrderByCreatedAtDesc(
            Long bookId,
            BookCoverStatus status,
            Long excludedCoverId
    );

    boolean existsByPublicId(String publicId);

    boolean existsByBookIdAndContentHash(
            Long bookId,
            String contentHash
    );

    boolean existsByBookIdAndOriginalSourceUrlAndStatus(
            Long bookId,
            String originalSourceUrl,
            BookCoverStatus status
    );

    boolean existsByBookIdAndPrimaryCoverTrue(Long bookId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BookCover cover
               set cover.primaryCover = false
             where cover.book.id = :bookId
               and cover.primaryCover = true
            """)
    int clearPrimaryCover(@Param("bookId") Long bookId);
}