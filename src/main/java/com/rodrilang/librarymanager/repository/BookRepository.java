package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByIsbn(String isbn);

    @Query("""
            SELECT DISTINCT b
            FROM Book b
            LEFT JOIN FETCH b.publisher
            LEFT JOIN FETCH b.authors
            WHERE b.id = :id
            """)
    Optional<Book> findByIdWithDetails(Long id);

    @Query("""
            SELECT DISTINCT b
            FROM Book b
            LEFT JOIN FETCH b.publisher
            LEFT JOIN FETCH b.authors
            WHERE b.isbn = :isbn
            """)
    Optional<Book> findByIsbnWithDetails(String isbn);

    boolean existsByIsbn(String isbn);

    List<Book> findByIsbnIn(Collection<String> isbns);

    Optional<Book> findFirstByTitleIgnoreCase(String title);

    Optional<Book> findFirstByTitleIgnoreCaseAndPublisher_NameIgnoreCase(String title, String publisherName);

    @Query(
            value = """
                    SELECT b.*
                    FROM books b
                    LEFT JOIN publishers p ON p.id = b.publisher_id
                    WHERE
                        lower(coalesce(b.isbn, '')) LIKE lower(concat('%', :query, '%'))
                        OR unaccent(lower(b.title)) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR unaccent(lower(coalesce(b.subtitle, ''))) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR unaccent(lower(p.name)) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR EXISTS (
                            SELECT 1
                            FROM book_authors ba
                            JOIN authors a ON a.id = ba.author_id
                            WHERE ba.book_id = b.id
                              AND unaccent(lower(a.name)) LIKE unaccent(lower(concat('%', :query, '%')))
                        )
                        OR similarity(unaccent(lower(b.title)), unaccent(lower(:query))) > 0.25
                        OR similarity(unaccent(lower(coalesce(b.subtitle, ''))), unaccent(lower(:query))) > 0.25
                        OR similarity(unaccent(lower(coalesce(p.name, ''))), unaccent(lower(:query))) > 0.25
                        OR EXISTS (
                            SELECT 1
                            FROM book_authors ba
                            JOIN authors a ON a.id = ba.author_id
                            WHERE ba.book_id = b.id
                              AND similarity(unaccent(lower(a.name)), unaccent(lower(:query))) > 0.25
                        )
                    ORDER BY
                        CASE
                            WHEN lower(coalesce(b.isbn, '')) = lower(:query) THEN 1
                            WHEN lower(coalesce(b.isbn, '')) LIKE lower(concat(:query, '%')) THEN 2
                            WHEN unaccent(lower(b.title)) LIKE unaccent(lower(concat(:query, '%'))) THEN 3
                            WHEN EXISTS (
                                SELECT 1
                                FROM book_authors ba
                                JOIN authors a ON a.id = ba.author_id
                                WHERE ba.book_id = b.id
                                  AND unaccent(lower(a.name)) LIKE unaccent(lower(concat(:query, '%')))
                            ) THEN 4
                            WHEN unaccent(lower(p.name)) LIKE unaccent(lower(concat(:query, '%'))) THEN 5
                            ELSE 6
                        END,
                        GREATEST(
                            similarity(unaccent(lower(b.title)), unaccent(lower(:query))),
                            similarity(unaccent(lower(coalesce(b.subtitle, ''))), unaccent(lower(:query))),
                            similarity(unaccent(lower(coalesce(p.name, ''))), unaccent(lower(:query)))
                        ) DESC,
                        b.title ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM books b
                    LEFT JOIN publishers p ON p.id = b.publisher_id
                    WHERE
                        lower(b.isbn) LIKE lower(concat('%', :query, '%'))
                        OR unaccent(lower(b.title)) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR unaccent(lower(coalesce(b.subtitle, ''))) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR unaccent(lower(p.name)) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR EXISTS (
                            SELECT 1
                            FROM book_authors ba
                            JOIN authors a ON a.id = ba.author_id
                            WHERE ba.book_id = b.id
                              AND unaccent(lower(a.name)) LIKE unaccent(lower(concat('%', :query, '%')))
                        )
                        OR similarity(unaccent(lower(b.title)), unaccent(lower(:query))) > 0.25
                        OR similarity(unaccent(lower(coalesce(b.subtitle, ''))), unaccent(lower(:query))) > 0.25
                        OR similarity(unaccent(lower(coalesce(p.name, ''))), unaccent(lower(:query))) > 0.25
                        OR EXISTS (
                            SELECT 1
                            FROM book_authors ba
                            JOIN authors a ON a.id = ba.author_id
                            WHERE ba.book_id = b.id
                              AND similarity(unaccent(lower(a.name)), unaccent(lower(:query))) > 0.25
                        )
                    """,
            nativeQuery = true
    )
    Page<Book> search(@Param("query") String query, Pageable pageable);

    @Query("""
                select b
                from Book b
                where b.active = true
                  and b.isbn is not null
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
}
