package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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

    Page<Author> findByNameContainingIgnoreCase(
            String name,
            Pageable pageable
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO authors (
                        name,
                        name_normalized,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :name,
                        :nameNormalized,
                        NOW(),
                        NOW()
                    )
                    ON CONFLICT (name_normalized) DO NOTHING
                    """,
            nativeQuery = true
    )
    void insertIfAbsent(
            String name,
            String nameNormalized
    );
}