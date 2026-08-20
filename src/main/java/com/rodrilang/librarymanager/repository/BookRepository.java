package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.projection.BookAuthorNameProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn13(String isbn13);

    boolean existsByIsbn10(String isbn10);

    Optional<Book> findByIsbn13(String isbn13);

    Optional<Book> findByIsbn10(String isbn10);

    List<Book> findByIsbn13In(Collection<String> isbn13Values);

    List<Book> findByIsbn10In(Collection<String> isbn10Values);

    @Query("""
            SELECT DISTINCT b
            FROM Book b
            LEFT JOIN FETCH b.publisher
            LEFT JOIN FETCH b.authors
            WHERE b.id = :id
            """)
    Optional<Book> findByIdWithDetails(Long id);

    Optional<Book> findFirstByTitleIgnoreCase(String title);

    Optional<Book> findFirstByTitleIgnoreCaseAndPublisher_NameIgnoreCase(String title, String publisherName);

    @Query(
            value = """
                    SELECT b.*
                    FROM books b
                    WHERE b.active = true
                      AND (
                            b.isbn_13 LIKE concat(:query, '%')
                            OR b.isbn_10 LIKE concat(:query, '%')
                      )
                    ORDER BY
                        CASE
                            WHEN b.isbn_13 = :query THEN 1
                            WHEN b.isbn_10 = :query THEN 1
                            ELSE 2
                        END,
                        b.title_sort ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM books b
                    WHERE b.active = true
                      AND (
                            b.isbn_13 LIKE concat(:query, '%')
                            OR b.isbn_10 LIKE concat(:query, '%')
                      )
                    """,
            nativeQuery = true
    )
    Page<Book> searchByIsbn(
            @Param("query") String query,
            Pageable pageable
    );

    @Query(
            value = """
                    WITH matches AS (
                        SELECT
                            b.id AS book_id,
                            CASE
                                WHEN immutable_unaccent(lower(b.title))
                                     = immutable_unaccent(lower(:query))
                                    THEN 1
                                WHEN immutable_unaccent(lower(b.title))
                                     LIKE concat(
                                         immutable_unaccent(lower(:query)),
                                         '%'
                                     )
                                    THEN 2
                                ELSE 3
                            END AS priority
                        FROM books b
                        WHERE b.active = true
                          AND immutable_unaccent(lower(b.title))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT
                            b.id AS book_id,
                            4 AS priority
                        FROM books b
                        WHERE b.active = true
                          AND immutable_unaccent(
                                  lower(coalesce(b.subtitle, ''))
                              )
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT
                            b.id AS book_id,
                            5 AS priority
                        FROM publishers p
                        JOIN books b ON b.publisher_id = p.id
                        WHERE b.active = true
                          AND immutable_unaccent(lower(p.name))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT
                            ba.book_id,
                            CASE
                                WHEN immutable_unaccent(lower(a.name))
                                     LIKE concat(
                                         immutable_unaccent(lower(:query)),
                                         '%'
                                     )
                                    THEN 3
                                ELSE 4
                            END AS priority
                        FROM authors a
                        JOIN book_authors ba ON ba.author_id = a.id
                        JOIN books b ON b.id = ba.book_id
                        WHERE b.active = true
                          AND immutable_unaccent(lower(a.name))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    ),
                    ranked_matches AS (
                        SELECT
                            book_id,
                            MIN(priority) AS priority
                        FROM matches
                        GROUP BY book_id
                    )
                    SELECT b.*
                    FROM ranked_matches rm
                    JOIN books b ON b.id = rm.book_id
                    ORDER BY
                        rm.priority ASC,
                        coalesce(b.title_sort, b.title) ASC,
                        b.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT matches.book_id)
                    FROM (
                        SELECT b.id AS book_id
                        FROM books b
                        WHERE b.active = true
                          AND immutable_unaccent(lower(b.title))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT b.id AS book_id
                        FROM books b
                        WHERE b.active = true
                          AND immutable_unaccent(
                                  lower(coalesce(b.subtitle, ''))
                              )
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT b.id AS book_id
                        FROM publishers p
                        JOIN books b ON b.publisher_id = p.id
                        WHERE b.active = true
                          AND immutable_unaccent(lower(p.name))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT ba.book_id
                        FROM authors a
                        JOIN book_authors ba ON ba.author_id = a.id
                        JOIN books b ON b.id = ba.book_id
                        WHERE b.active = true
                          AND immutable_unaccent(lower(a.name))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    ) matches
                    """,
            nativeQuery = true
    )
    Page<Book> searchText(
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
                select b
                from Book b
                where b.active = true
                  and (
                      b.isbn13 is not null
                      or b.isbn10 is not null
                  )
                  and (
                      b.subtitle is null
                      or b.description is null
                      or b.language is null
                      or b.pageCount is null
                      or b.publicationDate is null
                      or b.coverUrl is null
                      or b.publisher is null
                      or b.authors is empty
                  )
            """)
    List<Book> findBooksPendingMetadataEnrichment(Pageable pageable);

    @Query(
            value = """
                    SELECT b.*
                    FROM books b
                    LEFT JOIN editorial_prices ep ON ep.book_id = b.id
                      AND ep.active = true
                      AND ep.valid_from = (
                          SELECT MAX(ep2.valid_from)
                          FROM editorial_prices ep2
                          WHERE ep2.book_id = b.id
                            AND ep2.active = true
                            AND ep2.valid_from <= CURRENT_DATE
                      )
                    ORDER BY ep.price ASC NULLS LAST
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM books b
                    """,
            nativeQuery = true
    )
    Page<Book> findAllOrderByCurrentEditorialPriceAsc(Pageable pageable);

    @Query(
            value = """
                    SELECT b.*
                    FROM books b
                    LEFT JOIN editorial_prices ep ON ep.book_id = b.id
                      AND ep.active = true
                      AND ep.valid_from = (
                          SELECT MAX(ep2.valid_from)
                          FROM editorial_prices ep2
                          WHERE ep2.book_id = b.id
                            AND ep2.active = true
                            AND ep2.valid_from <= CURRENT_DATE
                      )
                    ORDER BY ep.price DESC NULLS LAST
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM books b
                    """,
            nativeQuery = true
    )
    Page<Book> findAllOrderByCurrentEditorialPriceDesc(Pageable pageable);

    @Query("""
            select b.id
            from Book b
            where b.active = true
              and (b.coverUrl is null or b.coverUrl = '')
              and coalesce(b.coverSearchAttempts, 0) < 3
              and (
                    b.coverSearchStatus is null
                    or b.coverSearchStatus = com.rodrilang.librarymanager.enums.CoverSearchStatus.PENDING
                    or b.coverSearchStatus = com.rodrilang.librarymanager.enums.CoverSearchStatus.ERROR
              )
            order by b.id
            """)
    List<Long> findPendingCoverEnrichmentIds(Pageable pageable);

    @Query("""
            select distinct b
            from Book b
            left join fetch b.publisher
            left join fetch b.authors
            where b.id in :ids
            """)
    List<Book> findBooksWithCoverDataByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select count(b)
            from Book b
            where b.active = true
              and (b.coverUrl is null or b.coverUrl = '')
              and coalesce(b.coverSearchAttempts, 0) < 3
              and (
                    b.coverSearchStatus is null
                    or b.coverSearchStatus = com.rodrilang.librarymanager.enums.CoverSearchStatus.PENDING
                    or b.coverSearchStatus = com.rodrilang.librarymanager.enums.CoverSearchStatus.ERROR
              )
            """)
    long countBooksPendingCoverEnrichment();

    @EntityGraph(attributePaths = {
            "authors",
            "publisher"
    })
    List<Book> findAllByActiveTrue();

    @EntityGraph(attributePaths = {"publisher", "authors"})
    @Query("SELECT b FROM Book b WHERE b.isbn13 = :isbn13")
    Optional<Book> findByIsbn13WithDetails(@Param("isbn13") String isbn13);

    @EntityGraph(attributePaths = {"publisher", "authors"})
    @Query("SELECT b FROM Book b WHERE b.isbn10 = :isbn10")
    Optional<Book> findByIsbn10WithDetails(@Param("isbn10") String isbn10);

    @Query("""
            select book
            from Book book
            where book.id = :bookId
            """)
    Optional<Book> findCoverCandidateById(@Param("bookId") Long bookId);

    @Query(
            value = """
                    SELECT b.id
                    FROM books b
                    WHERE b.active = TRUE
                      AND similarity(
                            b.title_sort,
                            :normalizedTitle
                          ) > 0.15
                    ORDER BY
                        similarity(
                            b.title_sort,
                            :normalizedTitle
                        ) DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Long> findTiendanubeCandidateIds(
            @Param("normalizedTitle") String normalizedTitle,
            @Param("limit") int limit
    );

    @Query("""
            SELECT DISTINCT b
            FROM Book b
            LEFT JOIN FETCH b.authors
            LEFT JOIN FETCH b.publisher
            WHERE b.id IN :ids
            """)
    List<Book> findAllWithDetailsByIdIn(
            @Param("ids") Collection<Long> ids
    );

    @Query("""
            SELECT
                b.id AS bookId,
                a.name AS authorName
            FROM Book b
            JOIN b.authors a
            WHERE b.id IN :bookIds
            ORDER BY a.name
            """)
    List<BookAuthorNameProjection> findAuthorNamesByBookIds(
            @Param("bookIds") Collection<Long> bookIds
    );
}
