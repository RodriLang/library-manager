package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByNameNormalized(
            String nameNormalized
    );

    List<Author> findByNameNormalizedIn(
            Collection<String> names
    );

    boolean existsByNameNormalized(
            String nameNormalized
    );

    @Query(
            value = """
                    SELECT a.*
                    FROM authors a
                    WHERE to_tsvector('simple', a.name_normalized)
                          @@ to_tsquery('simple', :tokenQuery)
                    ORDER BY
                        CASE
                            WHEN a.name_normalized = :query THEN 1
                            WHEN a.name_normalized LIKE CONCAT(:query, '%') THEN 2
                            ELSE 3
                        END,
                        a.name_normalized ASC,
                        a.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM authors a
                    WHERE to_tsvector('simple', a.name_normalized)
                          @@ to_tsquery('simple', :tokenQuery)
                    """,
            nativeQuery = true
    )
    Page<Author> searchByTokens(
            @Param("query") String query,
            @Param("tokenQuery") String tokenQuery,
            Pageable pageable
    );

}