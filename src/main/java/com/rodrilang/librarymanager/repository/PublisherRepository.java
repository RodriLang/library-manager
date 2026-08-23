package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.projection.PublisherCatalogConfigurationProjection;
import com.rodrilang.librarymanager.repository.projection.PublisherConfigurationDetailProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    Optional<Publisher> findByNameNormalized(String nameNormalized);

    List<Publisher> findByNameNormalizedIn(Collection<String> names);

    boolean existsByNameNormalized(String nameNormalized);

    @Query(
            value = """
                    SELECT p.*
                    FROM publishers p
                    WHERE immutable_unaccent(lower(p.name))
                        LIKE CONCAT(
                            '%',
                            immutable_unaccent(lower(:name)),
                            '%'
                        )
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM publishers p
                    WHERE immutable_unaccent(lower(p.name))
                        LIKE CONCAT(
                            '%',
                            immutable_unaccent(lower(:name)),
                            '%'
                        )
                    """,
            nativeQuery = true
    )
    Page<Publisher> searchByName(
            String name,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT
                        p.id AS id,
                        p.name AS name,
                        COUNT(b.id) AS bookCount,
                        EXISTS (
                            SELECT 1
                            FROM bookstore_excluded_publishers bep
                            WHERE bep.bookstore_id = :bookstoreId
                              AND bep.publisher_id = p.id
                        ) AS excluded
                    FROM publishers p
                    LEFT JOIN books b
                        ON b.publisher_id = p.id
                       AND b.active = true
                    WHERE p.id = :publisherId
                    GROUP BY p.id, p.name
                    """,
            nativeQuery = true
    )
    Optional<PublisherConfigurationDetailProjection> findCatalogConfigurationDetail(
            @Param("bookstoreId") Long bookstoreId,
            @Param("publisherId") Long publisherId
    );

    @Query(
            value = """
                    SELECT
                        p.id AS id,
                        p.name AS name,
                        COUNT(b.id) AS bookCount,
                        EXISTS (
                            SELECT 1
                            FROM bookstore_excluded_publishers bep
                            WHERE bep.bookstore_id = :bookstoreId
                              AND bep.publisher_id = p.id
                        ) AS excluded
                    FROM publishers p
                    JOIN books b
                        ON b.publisher_id = p.id
                       AND b.active = true
                    WHERE (
                        :query IS NULL
                        OR :query = ''
                        OR immutable_unaccent(lower(p.name))
                            LIKE CONCAT(
                                '%',
                                immutable_unaccent(lower(:query)),
                                '%'
                            )
                    )
                    AND (
                        :excluded IS NULL
                        OR :excluded = EXISTS (
                            SELECT 1
                            FROM bookstore_excluded_publishers bep
                            WHERE bep.bookstore_id = :bookstoreId
                              AND bep.publisher_id = p.id
                        )
                    )
                    GROUP BY p.id, p.name
                    ORDER BY
                        CASE WHEN :sort = 'nameAsc' THEN p.name END ASC,
                        CASE WHEN :sort = 'nameDesc' THEN p.name END DESC,
                        CASE WHEN :sort = 'bookCountAsc' THEN COUNT(b.id) END ASC,
                        CASE WHEN :sort = 'bookCountDesc' THEN COUNT(b.id) END DESC,
                        p.name ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM publishers p
                    WHERE EXISTS (
                        SELECT 1
                        FROM books b
                        WHERE b.publisher_id = p.id
                          AND b.active = true
                    )
                    AND (
                        :query IS NULL
                        OR :query = ''
                        OR immutable_unaccent(lower(p.name))
                            LIKE CONCAT(
                                '%',
                                immutable_unaccent(lower(:query)),
                                '%'
                            )
                    )
                    AND (
                        :excluded IS NULL
                        OR :excluded = EXISTS (
                            SELECT 1
                            FROM bookstore_excluded_publishers bep
                            WHERE bep.bookstore_id = :bookstoreId
                              AND bep.publisher_id = p.id
                        )
                    )
                    """,
            nativeQuery = true
    )
    Page<PublisherCatalogConfigurationProjection> searchForCatalogConfiguration(
            Long bookstoreId,
            String query,
            Boolean excluded,
            String sort,
            Pageable pageable
    );
}