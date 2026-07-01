package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByNameIgnoreCase(String name);

    List<Author> findByNameContainingIgnoreCase(String name);

    @Query("""
            SELECT a
            FROM Author a
            WHERE function('unaccent', lower(a.name))
                IN :names
            """)
    List<Author> findAllNormalizedIn(Collection<String> names);

    @Query("""
            SELECT COUNT(a) > 0
            FROM Author a
            WHERE function('unaccent', lower(a.name))
                =
                  function('unaccent', lower(:name))
            """)
    boolean existsNormalized(String name);
}
